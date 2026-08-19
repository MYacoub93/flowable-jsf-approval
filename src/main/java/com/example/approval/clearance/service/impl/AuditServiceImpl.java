package com.example.approval.clearance.service.impl;

import com.example.approval.audit.BpmAuditAction;
import com.example.approval.audit.BpmAuditConstants;
import com.example.approval.audit.BpmAuditProperties;
import com.example.approval.audit.model.BpmAuditLog;
import com.example.approval.audit.model.BpmAuditLogDtl;
import com.example.approval.audit.service.BpmAuditIdAllocator;
import com.example.approval.clearance.ClearanceConstants;
import com.example.approval.clearance.service.AuditService;
import com.example.approval.mapper.BpmAuditMapper;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.variable.api.history.HistoricVariableInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Implementation of {@link AuditService} writing to the pre-existing Oracle
 * {@code BPM_*} business audit tables through the
 * {@code externalSqlSessionFactory} (Oracle datasource).
 *
 * <p><b>Case linkage:</b> the schema has no {@code PROCESS_INSTANCE_ID}
 * column, so every insert resolves the case via the Flowable process
 * variable {@code bpmCaseId}. The id is reserved with
 * {@link #allocateCaseId()} before the instance starts (the synchronous
 * start already writes detail rows that need it) and the master row is
 * inserted by {@link #openCase} once the instance is running.</p>
 *
 * <p><b>Failure tolerance:</b> audit persistence runs inside the Flowable
 * command/transaction. A hard failure would roll the workflow step back -
 * usually desirable (no approvals without a trail).</p>
 */
@Service
public class AuditServiceImpl implements AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditServiceImpl.class);

    private final BpmAuditMapper bpmAuditMapper;
    private final BpmAuditProperties properties;
    private final BpmAuditIdAllocator idAllocator;
    private final RuntimeService runtimeService;
    private final HistoryService historyService;

    public AuditServiceImpl(BpmAuditMapper bpmAuditMapper,
                            BpmAuditProperties properties,
                            BpmAuditIdAllocator idAllocator,
                            RuntimeService runtimeService,
                            HistoryService historyService) {
        this.bpmAuditMapper = bpmAuditMapper;
        this.properties = properties;
        this.idAllocator = idAllocator;
        this.runtimeService = runtimeService;
        this.historyService = historyService;
    }

    // ------------------------------------------------------------------
    // Master case record (BPM_AUDIT_LOG)
    // ------------------------------------------------------------------

    @Override
    public Long allocateCaseId() {
        return idAllocator.nextCaseId();
    }

    @Override
    public Long openCase(String processDefinitionKey, String processInstanceId, String initiatorUsername) {
        // the id was reserved pre-start and now travels as bpmCaseId
        Long caseId = requireCaseId(processInstanceId);
        Integer requestorId = numericUserOf(initiatorUsername);
        Integer documentCode = properties.documentCodeOf(processDefinitionKey);

        BpmAuditLog master = new BpmAuditLog();
        master.setCaseId(caseId);
        master.setRequestorId(requestorId != null ? requestorId.longValue() : null);
        master.setDocumentCode(documentCode);
        master.setEntryUser(requestorId);
        master.setEntryDate(LocalDateTime.now());
        master.setTerminal(terminal());
        master.setOsUser(osUser());
        bpmAuditMapper.insertAuditLog(master);
        log.info("BPM audit: opened case {} (process instance {}, key {}, requestor {} / {})",
                caseId, processInstanceId, processDefinitionKey, initiatorUsername, requestorId);
        return caseId;
    }

    // ------------------------------------------------------------------
    // Task lifecycle (BPM_AUDIT_LOG_DTL)
    // ------------------------------------------------------------------

    @Override
    public BpmAuditLogDtl logTaskAssigned(String processInstanceId,
                                          String stage,
                                          String department,
                                          String candidateGroup,
                                          String taskId,
                                          String initiator) {
        String note = "Task " + nvl(taskId) + " assigned"
                + " | Process: " + ClearanceConstants.PROCESS_NAME
                + " | Stage: " + nvl(stage)
                + " | Department: " + nvl(department)
                + " | CandidateGroup: " + nvl(candidateGroup);
        return insert(BpmAuditAction.TASK_ASSIGNED, processInstanceId, initiator, note, 0);
    }

    @Override
    public BpmAuditLogDtl logTaskCompleted(String processInstanceId,
                                           String stage,
                                           String department,
                                           String completedBy,
                                           String decision,
                                           String comment,
                                           String taskId,
                                           String initiator) {
        BpmAuditAction action = ClearanceConstants.DECISION_APPROVE.equalsIgnoreCase(decision)
                ? BpmAuditAction.APPROVED
                : BpmAuditAction.REJECTED;
        String note = action.defaultDescription()
                + " | Process: " + ClearanceConstants.PROCESS_NAME
                + " | Stage: " + nvl(stage)
                + " | Department: " + nvl(department)
                + " | User: " + nvl(completedBy)
                + " | Comment: " + nvl(comment)
                + " | Task: " + nvl(taskId);
        return insert(action, processInstanceId, completedBy, note, 0);
    }

    @Override
    public BpmAuditLogDtl logProcessAction(String processInstanceId,
                                           String action,
                                           String stage,
                                           String department,
                                           String user,
                                           String initiator,
                                           String details) {
        BpmAuditAction mapped = mapAction(action);
        String note = nvl(details)
                + " | Stage: " + nvl(stage)
                + " | Department: " + nvl(department);
        return insert(mapped, processInstanceId, user, note,
                BpmAuditAction.CASE_FINISHED.equals(mapped) ? 1 : 0);
    }

    // ------------------------------------------------------------------
    // Read helpers
    // ------------------------------------------------------------------

    @Override
    public BpmAuditLog findCaseOfProcessInstance(String processInstanceId) {
        Long caseId = caseIdOfProcessInstance(processInstanceId);
        return caseId != null ? bpmAuditMapper.findAuditLogByCaseId(caseId) : null;
    }

    @Override
    public List<BpmAuditLogDtl> findDetailsOfProcessInstance(String processInstanceId) {
        Long caseId = caseIdOfProcessInstance(processInstanceId);
        return caseId != null
                ? bpmAuditMapper.findDetailsByCaseId(caseId)
                : List.of();
    }

    // ------------------------------------------------------------------
    // Case-id resolution (process variable bpmCaseId, history fallback)
    // ------------------------------------------------------------------

    @Override
    public Long caseIdOfProcessInstance(String processInstanceId) {
        if (processInstanceId == null || processInstanceId.isBlank()) {
            return null;
        }
        try {
            Object runtime = runtimeService.getVariable(processInstanceId, BpmAuditConstants.VAR_CASE_ID);
            if (runtime instanceof Number n) {
                return n.longValue();
            }
        } catch (org.flowable.common.engine.api.FlowableObjectNotFoundException e) {
            // process instance already finished - fall through to history
        }
        return historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(processInstanceId)
                .variableName(BpmAuditConstants.VAR_CASE_ID)
                .list().stream()
                .map(HistoricVariableInstance::getValue)
                .filter(v -> v instanceof Number)
                .map(v -> ((Number) v).longValue())
                .findFirst()
                .orElse(null);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private BpmAuditLogDtl insert(BpmAuditAction action,
                                  String processInstanceId,
                                  String user,
                                  String note,
                                  int isFinished) {
        Long caseId = requireCaseId(processInstanceId);
        BpmAuditLogDtl dtl = new BpmAuditLogDtl();
        dtl.setSerial(idAllocator.nextDetailSerial());
        dtl.setCaseId(caseId);
        dtl.setActionCode(action.code());
        dtl.setNote(truncate(note, BpmAuditConstants.NOTE_MAX_LENGTH));
        dtl.setEntryUser(numericUserOf(user));
        dtl.setEntryDate(LocalDateTime.now());
        dtl.setTerminal(truncate(terminal(), BpmAuditConstants.TERMINAL_MAX_LENGTH_DTL));
        dtl.setOsUser(osUser());
        dtl.setIsFinished(isFinished);
        bpmAuditMapper.insertAuditLogDtl(dtl);
        log.debug("BPM audit detail written: {}", dtl);
        return dtl;
    }

    private Long requireCaseId(String processInstanceId) {
        Long caseId = caseIdOfProcessInstance(processInstanceId);
        if (caseId == null) {
            throw new IllegalStateException("No bpmCaseId variable on process instance "
                    + processInstanceId + " - cannot write BPM audit row");
        }
        return caseId;
    }

    /** Maps legacy ClearanceConstants.ACTION_* strings onto numeric codes. */
    private BpmAuditAction mapAction(String action) {
        if (action == null) {
            return BpmAuditAction.GENERIC;
        }
        return switch (action) {
            case ClearanceConstants.ACTION_PROCESS_STARTED -> BpmAuditAction.CASE_OPENED;
            case ClearanceConstants.ACTION_DEPARTMENTS_RESOLVED -> BpmAuditAction.GENERIC;
            case ClearanceConstants.ACTION_TASK_ASSIGNED -> BpmAuditAction.TASK_ASSIGNED;
            case ClearanceConstants.ACTION_APPROVED -> BpmAuditAction.APPROVED;
            case ClearanceConstants.ACTION_REJECTED -> BpmAuditAction.REJECTED;
            case ClearanceConstants.ACTION_REQUEST_AMENDED -> BpmAuditAction.REQUEST_AMENDED;
            case ClearanceConstants.ACTION_TASK_CANCELLED -> BpmAuditAction.TASK_CANCELLED;
            case ClearanceConstants.ACTION_PROCESS_COMPLETED -> BpmAuditAction.CASE_FINISHED;
            case ClearanceConstants.ACTION_FYI_CREATED -> BpmAuditAction.FYI_CREATED;
            case "ATTACHMENT_UPLOADED" -> BpmAuditAction.ATTACHMENT_UPLOADED;
            case ClearanceConstants.ACTION_FYI_ACKNOWLEDGED,
                 ClearanceConstants.ACTION_RESULT_ACKNOWLEDGED -> BpmAuditAction.FYI_ACKNOWLEDGED;
            default -> BpmAuditAction.GENERIC;
        };
    }

    private Integer numericUserOf(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        try {
            return bpmAuditMapper.findNumericUserId(username);
        } catch (Exception e) {
            log.warn("Could not resolve numeric user id of '{}' - storing null", username, e);
            return null;
        }
    }

    private String terminal() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }

    private String osUser() {
        return System.getProperty("user.name", "unknown");
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String nvl(String value) {
        return value == null ? "" : value;
    }
}
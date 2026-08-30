package com.example.approval.audit.service;

import com.example.approval.audit.BpmAuditAction;
import com.example.approval.audit.BpmAuditConstants;
import com.example.approval.audit.BpmAuditProperties;
import com.example.approval.audit.model.BpmAuditLog;
import com.example.approval.audit.model.BpmAuditLogDtl;
import com.example.approval.mapper.BpmAuditMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Implementation of {@link BpmAuditService} writing to the Oracle
 * {@code F_BPM_*} business audit tables through the
 * {@code externalSqlSessionFactory} (Oracle datasource).
 *
 * <p><b>Process agnostic:</b> nothing in this class knows a specific
 * process. Callers pass the Flowable process definition / instance ids and
 * the shared semantic action keys / decision values of
 * {@link BpmAuditConstants}; departments resolve to their own
 * {@code BPM_ACTIONS} rows via {@link BpmAuditAction}.</p>
 *
 * <p><b>Case linkage:</b> {@code CASE_ID} is the Flowable process instance
 * id of the started case, supplied by the caller on every call - it is
 * never generated (no sequence / serial). Only the {@code SERIAL} columns
 * of the detail / attachment tables are allocated via
 * {@link BpmAuditIdAllocator}.</p>
 *
 * <p><b>Failure tolerance:</b> audit persistence runs inside the Flowable
 * command/transaction. A hard failure would roll the workflow step back -
 * usually desirable (no approvals without a trail).</p>
 *
 * <p><b>Global switch:</b> setting {@code bpm.audit.enabled=false} turns
 * every write of this service into a no-op (debug log only) while the
 * workflow itself keeps running.</p>
 */
@Service
public class BpmAuditServiceImpl implements BpmAuditService {

    private static final Logger log = LoggerFactory.getLogger(BpmAuditServiceImpl.class);

    /**
     * Fallback for the NOT NULL {@code ENTRY_USER} / {@code REQUESTOR_ID}
     * columns when the acting username cannot be resolved to a numeric id.
     */
    private static final int UNKNOWN_USER_ID = 0;

    private final BpmAuditMapper bpmAuditMapper;
    private final BpmAuditProperties properties;
    private final BpmAuditIdAllocator idAllocator;

    public BpmAuditServiceImpl(BpmAuditMapper bpmAuditMapper,
                               BpmAuditProperties properties,
                               BpmAuditIdAllocator idAllocator) {
        this.bpmAuditMapper = bpmAuditMapper;
        this.properties = properties;
        this.idAllocator = idAllocator;
    }

    // ------------------------------------------------------------------
    // Master case record (F_BPM_AUDIT_LOG)
    // ------------------------------------------------------------------

    @Override
    public String openCase(String processDefinitionKey,
                           String processInstanceId,
                           String initiatorUsername,
                           String note) {
        if (!properties.isEnabled()) {
            log.debug("BPM audit disabled (bpm.audit.enabled=false) - skipping openCase for {}",
                    processInstanceId);
            return processInstanceId;
        }
        // CASE_ID = the caller-supplied Flowable process instance id
        String caseId = requireCaseId(processInstanceId);
        Integer requestorId = numericUserOf(initiatorUsername);
        Integer documentCode = properties.documentCodeOf(processDefinitionKey);

        BpmAuditLog master = new BpmAuditLog();
        master.setCaseId(caseId);
        master.setRequestorId(requestorId != null ? requestorId.longValue() : (long) UNKNOWN_USER_ID);
        master.setDocumentCode(documentCode);
        master.setEntryUser(requestorId != null ? requestorId : UNKNOWN_USER_ID);
        master.setEntryDate(LocalDateTime.now());
        master.setTerminal(truncate(terminal(), BpmAuditConstants.TERMINAL_MAX_LENGTH_DTL));
        master.setOsUser(osUser());
        bpmAuditMapper.insertAuditLog(master);

        // opening action of the case: ENTERED (0) - the first detail row of
        // F_BPM_AUDIT_LOG_DTL, carrying the note typed on the start form
        insert(BpmAuditAction.ENTERED, processInstanceId, initiatorUsername,
                note != null && !note.trim().isEmpty() ? note.trim() : null, 0);

        log.info("BPM audit: opened case {} (process instance {}, key {}, requestor {} / {})",
                caseId, processInstanceId, processDefinitionKey, initiatorUsername, requestorId);
        return caseId;
    }

    // ------------------------------------------------------------------
    // Task lifecycle (F_BPM_AUDIT_LOG_DTL)
    // ------------------------------------------------------------------

    @Override
    public BpmAuditLogDtl logTaskAssigned(String processInstanceId,
                                          String stage,
                                          String department,
                                          String candidateGroup,
                                          String taskId,
                                          String initiator) {
        String note = "Task " + nvl(taskId) + " assigned"
                + " | Stage: " + nvl(stage)
                + " | Department: " + nvl(department)
                + " | CandidateGroup: " + nvl(candidateGroup);
        // "task assigned" maps onto the department's Task Received row
        // (استلام مهمة) of the BPM_ACTIONS lookup
        return insert(BpmAuditAction.of(department, BpmAuditAction.ActionType.TASK_RECEIVED),
                processInstanceId, initiator, note, 0);
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
        // decision maps onto the department's own Approval / Rejection row
        // of the BPM_ACTIONS lookup
        BpmAuditAction action = BpmAuditAction.of(department,
                BpmAuditConstants.DECISION_APPROVE.equalsIgnoreCase(decision)
                        ? BpmAuditAction.ActionType.APPROVAL
                        : BpmAuditAction.ActionType.REJECTION);
        String note = action.defaultDescription()
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
        BpmAuditAction mapped = mapAction(action, department);
        String note = nvl(details)
                + " | Stage: " + nvl(stage)
                + " | Department: " + nvl(department);
        return insert(mapped, processInstanceId, user, note,
                mapped == BpmAuditAction.APPLICANT_VIEWED_FINAL_RESULT ? 1 : 0);
    }

    // ------------------------------------------------------------------
    // Read helpers
    // ------------------------------------------------------------------

    @Override
    public BpmAuditLog findCaseOfProcessInstance(String processInstanceId) {
        if (processInstanceId == null || processInstanceId.isBlank()) {
            return null;
        }
        return bpmAuditMapper.findAuditLogByCaseId(processInstanceId);
    }

    @Override
    public List<BpmAuditLogDtl> findDetailsOfProcessInstance(String processInstanceId) {
        if (processInstanceId == null || processInstanceId.isBlank()) {
            return List.of();
        }
        return bpmAuditMapper.findDetailsByCaseId(processInstanceId);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private BpmAuditLogDtl insert(BpmAuditAction action,
                                  String processInstanceId,
                                  String user,
                                  String note,
                                  int isFinished) {
        if (!properties.isEnabled()) {
            log.debug("BPM audit disabled (bpm.audit.enabled=false) - skipping {} on case {}",
                    action, processInstanceId);
            return null;
        }
        String caseId = requireCaseId(processInstanceId);
        Integer entryUser = numericUserOf(user);
        BpmAuditLogDtl dtl = new BpmAuditLogDtl();
        dtl.setSerial(idAllocator.nextDetailSerial());
        dtl.setCaseId(caseId);
        dtl.setActionCode(action.code());
        dtl.setNote(truncate(note, BpmAuditConstants.NOTE_MAX_LENGTH));
        dtl.setEntryUser(entryUser != null ? entryUser : UNKNOWN_USER_ID);
        dtl.setEntryDate(LocalDateTime.now());
        dtl.setTerminal(truncate(terminal(), BpmAuditConstants.TERMINAL_MAX_LENGTH_DTL));
        dtl.setOsUser(osUser());
        dtl.setIsFinished(isFinished);
        bpmAuditMapper.insertAuditLogDtl(dtl);
        log.debug("BPM audit detail written: {}", dtl);
        return dtl;
    }

    /**
     * CASE_ID must always be the caller-supplied process instance id of the
     * Flowable case that was started - there is nothing to allocate.
     */
    private String requireCaseId(String processInstanceId) {
        if (processInstanceId == null || processInstanceId.isBlank()) {
            throw new IllegalStateException("Missing processInstanceId (business CASE_ID) "
                    + "- cannot write BPM audit row");
        }
        return truncate(processInstanceId, BpmAuditConstants.CASE_ID_MAX_LENGTH);
    }

    /**
     * Maps the semantic {@code BpmAuditConstants.ACTION_*} keys onto the
     * pre-populated {@code BPM_ACTIONS} codes. Department-aware events
     * (assigned / approved / rejected) resolve the department's own row via
     * {@link BpmAuditAction#of(String, BpmAuditAction.ActionType)}; events
     * with no BPM_ACTIONS equivalent (departments resolved, task cancelled,
     * FYI created, attachment uploaded) fall back to {@code ENTERED} (0) -
     * the note column carries the specifics.
     */
    private BpmAuditAction mapAction(String action, String department) {
        if (action == null) {
            return BpmAuditAction.ENTERED;
        }
        return switch (action) {
            case BpmAuditConstants.ACTION_PROCESS_STARTED -> BpmAuditAction.ENTERED;
            case BpmAuditConstants.ACTION_DEPARTMENTS_RESOLVED -> BpmAuditAction.ENTERED;
            case BpmAuditConstants.ACTION_TASK_ASSIGNED ->
                    BpmAuditAction.of(department, BpmAuditAction.ActionType.TASK_RECEIVED);
            case BpmAuditConstants.ACTION_APPROVED ->
                    BpmAuditAction.of(department, BpmAuditAction.ActionType.APPROVAL);
            case BpmAuditConstants.ACTION_REJECTED ->
                    BpmAuditAction.of(department, BpmAuditAction.ActionType.REJECTION);
            case BpmAuditConstants.ACTION_REQUEST_AMENDED -> BpmAuditAction.APPLICANT_CONTINUATION;
            case BpmAuditConstants.ACTION_TASK_CANCELLED -> BpmAuditAction.ENTERED;
            case BpmAuditConstants.ACTION_PROCESS_COMPLETED -> BpmAuditAction.APPLICANT_VIEWED_FINAL_RESULT;
            case BpmAuditConstants.ACTION_FYI_CREATED -> BpmAuditAction.ENTERED;
            case BpmAuditConstants.ACTION_ATTACHMENT_UPLOADED -> BpmAuditAction.ENTERED;
            case BpmAuditConstants.ACTION_FYI_ACKNOWLEDGED -> BpmAuditAction.ENTERED;
            case BpmAuditConstants.ACTION_RESULT_ACKNOWLEDGED -> BpmAuditAction.APPLICANT_VIEWED_FINAL_RESULT;
            default -> BpmAuditAction.ENTERED;
        };
    }

    private Integer numericUserOf(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        try {
            return bpmAuditMapper.findNumericUserId(username);
        } catch (Exception e) {
            log.warn("Could not resolve numeric user id of '{}' - storing fallback {}", username,
                    UNKNOWN_USER_ID, e);
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
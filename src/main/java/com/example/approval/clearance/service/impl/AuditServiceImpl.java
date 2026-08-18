package com.example.approval.clearance.service.impl;

import com.example.approval.clearance.ClearanceConstants;
import com.example.approval.clearance.model.AuditRecord;
import com.example.approval.mapper.AuditLogMapper;
import com.example.approval.clearance.service.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MyBatis-backed implementation of {@link AuditService} writing to
 * {@code CLRT_AUDIT_LOG} on the primary (MySQL) datasource.
 *
 * <p><b>Failure tolerance:</b> audit persistence runs inside the Flowable
 * command/transaction. A hard failure in the audit insert would roll the
 * whole workflow step back, which is usually desirable (you don't want
 * approvals without a trail). If your business prefers availability of the
 * workflow over completeness of the trail, catch exceptions around the
 * public methods instead.</p>
 */
@Service
public class AuditServiceImpl implements AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditServiceImpl.class);

    private final AuditLogMapper auditLogMapper;

    public AuditServiceImpl(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    @Override
    public AuditRecord logTaskAssigned(String processInstanceId,
                                       String stage,
                                       String department,
                                       String candidateGroup,
                                       String taskId,
                                       String initiator) {
        String details = "Action: " + ClearanceConstants.ACTION_TASK_ASSIGNED
                + " | Process: " + ClearanceConstants.PROCESS_NAME
                + " | Department: " + nvl(department)
                + " | CandidateGroup: " + nvl(candidateGroup)
                + " | Initiator: " + nvl(initiator)
                + " | Task: " + nvl(taskId);
        return insert(ClearanceConstants.ACTION_TASK_ASSIGNED,
                processInstanceId, stage, department, initiator, initiator, taskId, details);
    }

    @Override
    public AuditRecord logTaskCompleted(String processInstanceId,
                                        String stage,
                                        String department,
                                        String completedBy,
                                        String decision,
                                        String comment,
                                        String taskId,
                                        String initiator) {
        String action = ClearanceConstants.DECISION_APPROVE.equalsIgnoreCase(decision)
                ? ClearanceConstants.ACTION_APPROVED
                : ClearanceConstants.ACTION_REJECTED;
        String details = "Action: " + action
                + " | Process: " + ClearanceConstants.PROCESS_NAME
                + " | Department: " + nvl(department)
                + " | User: " + nvl(completedBy)
                + " | Comment: " + nvl(comment)
                + " | Task: " + nvl(taskId);
        return insert(action, processInstanceId, stage, department, completedBy, initiator, taskId, details);
    }

    @Override
    public AuditRecord logProcessAction(String processInstanceId,
                                        String action,
                                        String stage,
                                        String department,
                                        String user,
                                        String initiator,
                                        String details) {
        return insert(action, processInstanceId, stage, department, user, initiator, null, details);
    }

    @Override
    public List<AuditRecord> findByProcessInstanceId(String processInstanceId) {
        return auditLogMapper.findByProcessInstanceId(processInstanceId);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private AuditRecord insert(String action,
                               String processInstanceId,
                               String stage,
                               String department,
                               String user,
                               String initiator,
                               String taskId,
                               String details) {
        AuditRecord record = new AuditRecord(
                processInstanceId,
                ClearanceConstants.PROCESS_NAME,
                action,
                stage,
                department,
                user,
                initiator,
                taskId,
                details,
                LocalDateTime.now());
        auditLogMapper.insertAuditRecord(record);
        log.debug("Audit written: {}", record);
        return record;
    }

    private String nvl(String value) {
        return value == null ? "" : value;
    }
}
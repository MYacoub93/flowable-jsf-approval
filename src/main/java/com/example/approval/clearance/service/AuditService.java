package com.example.approval.clearance.service;

import com.example.approval.clearance.model.AuditRecord;

import java.util.List;

/**
 * Central, reusable audit trail for the Clearance Letter process.
 *
 * <p>All audit writes go through this service - from Flowable listeners
 * (task created / completed), delegates (department resolution, completion
 * actions) and the JSF layer (acknowledgement of results). The BPMN never
 * inserts audit rows itself.</p>
 */
public interface AuditService {

    /**
     * Audit row for a task becoming available to an approver group
     * (action {@code TASK_ASSIGNED}).
     *
     * @param processInstanceId process instance id
     * @param stage             stage code (see ClearanceConstants.STAGE_*)
     * @param department        department / approver group name
     * @param candidateGroup    Flowable candidate group id
     * @param taskId            task id
     * @param initiator         process initiator username
     * @return the persisted record (with generated id)
     */
    AuditRecord logTaskAssigned(String processInstanceId,
                                String stage,
                                String department,
                                String candidateGroup,
                                String taskId,
                                String initiator);

    /**
     * Audit row for a completed approval task (action {@code APPROVED} or
     * {@code REJECTED} depending on the decision).
     *
     * @param processInstanceId process instance id
     * @param stage             stage code
     * @param department        department / approver group name
     * @param completedBy       user who completed the task
     * @param decision          "approve" or "reject"
     * @param comment           free text comment (nullable)
     * @param taskId            task id
     * @param initiator         process initiator username
     * @return the persisted record
     */
    AuditRecord logTaskCompleted(String processInstanceId,
                                 String stage,
                                 String department,
                                 String completedBy,
                                 String decision,
                                 String comment,
                                 String taskId,
                                 String initiator);

    /**
     * Generic audit action with explicit fields (department resolution,
     * amendment, completion, FYI creation, acknowledgements...).
     *
     * @param processInstanceId process instance id
     * @param action            action code (see ClearanceConstants.ACTION_*)
     * @param stage             stage code (nullable)
     * @param department        involved department / group (nullable)
     * @param user              acting user (nullable)
     * @param initiator         original initiator (nullable)
     * @param details           free text details (nullable)
     * @return the persisted record
     */
    AuditRecord logProcessAction(String processInstanceId,
                                 String action,
                                 String stage,
                                 String department,
                                 String user,
                                 String initiator,
                                 String details);

    /** All audit rows of one process instance, oldest first. */
    List<AuditRecord> findByProcessInstanceId(String processInstanceId);
}
package com.example.approval.clearance.service;

import com.example.approval.audit.model.BpmAuditLog;
import com.example.approval.audit.model.BpmAuditLogDtl;

import java.util.List;

/**
 * Central audit trail for workflow processes, backed by the Oracle
 * {@code F_BPM_*} business tables:
 *
 * <ul>
 *   <li>{@code F_BPM_AUDIT_LOG} - one master row per started process (case);</li>
 *   <li>{@code F_BPM_AUDIT_LOG_DTL} - one row per audited workflow action.</li>
 * </ul>
 *
 * <p>All audit writes go through this service - from Flowable listeners
 * (task created / completed), delegates (department resolution, completion
 * actions), the JSF layer (acknowledgement of results) and the REST layer
 * (attachment uploads). The BPMN never inserts audit rows itself.</p>
 *
 * <p><b>Case linkage:</b> {@code CASE_ID} is a {@code VARCHAR2(64)} holding
 * the Flowable process instance id of the case. It is passed in by the
 * caller on every call and is <b>never</b> generated (no sequence / serial
 * involved) - the business case id simply <i>is</i> the process instance id
 * of the Flowable case that was started.</p>
 */
public interface AuditService {

    /**
     * Opens a new case <b>after</b> the process instance was started:
     * inserts the master row into {@code F_BPM_AUDIT_LOG} using the
     * process instance id as {@code CASE_ID} (caller-supplied, never
     * generated) and returns it.
     *
     * @param processDefinitionKey Flowable process definition key (mapped to
     *                             {@code BPM_DOCUMENTS.DOCUMENT_CODE})
     * @param processInstanceId    id of the already started process instance;
     *                             used directly as {@code CASE_ID}
     * @param initiatorUsername    username of the requestor
     * @return the business case id (== processInstanceId)
     */
    String openCase(String processDefinitionKey, String processInstanceId, String initiatorUsername);

    /**
     * Audit row for a task becoming available to an approver group
     * (action {@code TASK_ASSIGNED}).
     *
     * @param processInstanceId process instance id (== business case id)
     * @param stage             stage code (see ClearanceConstants.STAGE_*)
     * @param department        department / approver group name
     * @param candidateGroup    Flowable candidate group id
     * @param taskId            task id
     * @param initiator         process initiator username
     * @return the persisted detail record
     */
    BpmAuditLogDtl logTaskAssigned(String processInstanceId,
                                   String stage,
                                   String department,
                                   String candidateGroup,
                                   String taskId,
                                   String initiator);

    /**
     * Audit row for a completed approval task (action {@code APPROVED} or
     * {@code REJECTED} depending on the decision).
     *
     * @param processInstanceId process instance id (== business case id)
     * @param stage             stage code
     * @param department        department / approver group name
     * @param completedBy       user who completed the task
     * @param decision          "approve" or "reject"
     * @param comment           free text comment (nullable)
     * @param taskId            task id
     * @param initiator         process initiator username
     * @return the persisted detail record
     */
    BpmAuditLogDtl logTaskCompleted(String processInstanceId,
                                    String stage,
                                    String department,
                                    String completedBy,
                                    String decision,
                                    String comment,
                                    String taskId,
                                    String initiator);

    /**
     * Generic audit action with explicit fields (department resolution,
     * amendment, completion, FYI creation, acknowledgements, uploads...).
     *
     * @param processInstanceId process instance id (== business case id)
     * @param action            semantic action key (ClearanceConstants.ACTION_*
     *                          or "ATTACHMENT_UPLOADED"); the implementation
     *                          maps it onto the pre-populated BPM_ACTIONS codes
     * @param stage             stage code (nullable)
     * @param department        involved department / group (nullable)
     * @param user              acting user (nullable)
     * @param initiator         original initiator (nullable)
     * @param details           free text details (nullable)
     * @return the persisted detail record
     */
    BpmAuditLogDtl logProcessAction(String processInstanceId,
                                    String action,
                                    String stage,
                                    String department,
                                    String user,
                                    String initiator,
                                    String details);

    /** Master case record ({@code F_BPM_AUDIT_LOG}) of a process instance. */
    BpmAuditLog findCaseOfProcessInstance(String processInstanceId);

    /** All audit detail rows ({@code F_BPM_AUDIT_LOG_DTL}) of a case, oldest first. */
    List<BpmAuditLogDtl> findDetailsOfProcessInstance(String processInstanceId);
}
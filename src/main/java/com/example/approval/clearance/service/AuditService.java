package com.example.approval.clearance.service;

import com.example.approval.audit.model.BpmAuditLog;
import com.example.approval.audit.model.BpmAuditLogDtl;

import java.util.List;

/**
 * Central audit trail for workflow processes, backed by the pre-existing
 * Oracle {@code BPM_*} business tables:
 *
 * <ul>
 *   <li>{@code BPM_AUDIT_LOG} - one master row per started process (case);</li>
 *   <li>{@code BPM_AUDIT_LOG_DTL} - one row per audited workflow action.</li>
 * </ul>
 *
 * <p>All audit writes go through this service - from Flowable listeners
 * (task created / completed), delegates (department resolution, completion
 * actions), the JSF layer (acknowledgement of results) and the REST layer
 * (attachment uploads). The BPMN never inserts audit rows itself.</p>
 *
 * <p><b>Case linkage:</b> the Oracle schema has no {@code PROCESS_INSTANCE_ID}
 * column, so case rows are linked to Flowable instances via the process
 * variable {@code bpmCaseId}. A case is opened in two steps:
 * {@link #allocateCaseId()} reserves the numeric id <b>before</b> the
 * instance starts (it travels through the whole process as the
 * {@code bpmCaseId} start variable), and {@link #openCase} is called
 * <b>after</b> the process was started to write the {@code BPM_AUDIT_LOG}
 * master row using that id.</p>
 */
public interface AuditService {

    /**
     * Reserves the next numeric {@code BPM_AUDIT_LOG.CASE_ID} without
     * inserting anything yet. Call this <b>before</b> starting the process
     * instance and store the returned id as the {@code bpmCaseId} start
     * variable: the synchronous part of the start (service tasks, first
     * task-create listeners) already writes {@code BPM_AUDIT_LOG_DTL} rows
     * that need the case linkage.
     *
     * @return the reserved business case id
     */
    Long allocateCaseId();

    /**
     * Opens a new case <b>after</b> the process instance was started:
     * inserts the master row into {@code BPM_AUDIT_LOG} with the
     * {@code bpmCaseId} already present on the instance (the id reserved
     * via {@link #allocateCaseId()}) and returns it.
     *
     * @param processDefinitionKey Flowable process definition key (mapped to
     *                             {@code BPM_DOCUMENTS.DOCUMENT_CODE})
     * @param processInstanceId    id of the already started process instance
     * @param initiatorUsername    username of the requestor
     * @return the business case id of the inserted master row
     */
    Long openCase(String processDefinitionKey, String processInstanceId, String initiatorUsername);

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
     * @param processInstanceId process instance id
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
     * @param processInstanceId process instance id
     * @param action            action code (see ClearanceConstants.ACTION_*
     *                          or BpmAuditAction.name())
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

    /** Master case record ({@code BPM_AUDIT_LOG}) of a process instance. */
    BpmAuditLog findCaseOfProcessInstance(String processInstanceId);

    /** All audit detail rows ({@code BPM_AUDIT_LOG_DTL}) of a case, oldest first. */
    List<BpmAuditLogDtl> findDetailsOfProcessInstance(String processInstanceId);

    /**
     * Business {@code CASE_ID} behind a Flowable process instance: resolves
     * the {@code bpmCaseId} variable from the runtime, falling back to the
     * history once the instance has finished. Returns {@code null} if the
     * instance has no case linkage.
     */
    Long caseIdOfProcessInstance(String processInstanceId);
}
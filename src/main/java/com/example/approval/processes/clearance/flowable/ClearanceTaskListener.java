package com.example.approval.processes.clearance.flowable;

import com.example.approval.processes.clearance.ClearanceConstants;
import com.example.approval.processes.clearance.model.DepartmentDecision;
import com.example.approval.audit.service.BpmAuditService;
import com.example.approval.notification.model.NotificationMessage;
import com.example.approval.notification.service.NotificationService;
import com.example.approval.processes.clearance.service.ClearanceApproverResolverService;
import org.flowable.engine.delegate.TaskListener;
import org.flowable.task.service.delegate.DelegateTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

import static com.example.approval.processes.clearance.ClearanceConstants.*;

/**
 * The one reusable TaskListener attached to <b>every</b> approval task of the
 * Clearance Letter process (department multi-instance tasks, Finance,
 * Admission and the initiator amendment task):
 *
 * <ul>
 *   <li><b>create</b> - sends the "task is waiting" e-mail via
 *       {@link NotificationService} and writes the {@code TASK_ASSIGNED}
 *       audit row via {@link BpmAuditService}. Approval tasks mail their
 *       candidate group (or the single assigned approver); the amendment
 *       task mails the initiator directly;</li>
 *   <li><b>complete</b> - writes the {@code APPROVED}/{@code REJECTED} audit
 *       row, stores the {@link DepartmentDecision} in the
 *       {@code departmentDecisions} map and raises
 *       {@code anyDepartmentRejected} as soon as one department rejects
 *       (the flag is consumed by the {@code gatewayAllApproved} gateway
 *       <b>after</b> the multi-instance synchronization barrier released -
 *       it does NOT cancel sibling tasks: every department must submit
 *       before the stage is left);</li>
 *   <li><b>delete</b> - writes a {@code TASK_CANCELLED} audit row if a
 *       department task is ever removed while still open (e.g. process
 *       termination - no longer triggered by the completion condition,
 *       which now waits for all instances).</li>
 * </ul>
 *
 * <p>Attached once per task in the BPMN via
 * {@code delegateExpression="${clearanceTaskListener}"} - no duplicated
 * listener code anywhere in the model.</p>
 */
@Component("clearanceTaskListener")
public class ClearanceTaskListener implements TaskListener {

    private static final long serialVersionUID = 1L;

    private static final Logger log = LoggerFactory.getLogger(ClearanceTaskListener.class);

    private final NotificationService notificationService;
    private final BpmAuditService auditService;
    private final ClearanceApproverResolverService approverResolver;

    /**
     * Production constructor (Spring): the approver resolver assigns the
     * HOD / dean department tasks directly to one specific person.
     * {@code @Autowired} is required because the class also exposes a
     * 2-arg test constructor - without it Spring cannot choose and falls
     * back to the (non-existent) no-arg constructor.
     */
    @Autowired
    public ClearanceTaskListener(NotificationService notificationService,
            BpmAuditService auditService,
            ClearanceApproverResolverService approverResolver) {
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.approverResolver = approverResolver;
    }

    /**
     * Test/fallback constructor: no single-approver resolution - every
     * department task stays a pure candidate-group task.
     */
    public ClearanceTaskListener(NotificationService notificationService,
            BpmAuditService auditService) {
        this(notificationService, auditService, null);
    }

    @Override
    public void notify(DelegateTask delegateTask) {
        String event = delegateTask.getEventName();
        if (EVENTNAME_CREATE.equals(event)) {
            onCreated(delegateTask);
        } else if (EVENTNAME_COMPLETE.equals(event)) {
            onCompleted(delegateTask);
        } else if (EVENTNAME_DELETE.equals(event)) {
            onDeleted(delegateTask);
        }
    }

    // ------------------------------------------------------------------
    // create: e-mail + TASK_ASSIGNED audit
    // ------------------------------------------------------------------

    private void onCreated(DelegateTask task) {
        String stage = stageOf(task);
        String department = departmentOf(task, stage);
        String candidateGroup = candidateGroupOf(task, stage, department);
        String initiator = str(task.getVariable(VAR_INITIATOR));
        String pid = task.getProcessInstanceId();

        // HOD / dean tasks go to ONE specific person instead of the whole
        // candidate group. Runs first so the audit row and the e-mail see
        // the final assignee. Best effort: if no single approver can be
        // resolved the task simply remains a group task - task creation
        // never fails because of it.
        assignSingleApproverIfConfigured(task, stage, department);

        // audit first: TASK_ASSIGNED for every task handed to an approver.
        // The note is optional: if one was persisted on the task (task local
        // variable note/notes or the task description) it is written to
        // F_BPM_AUDIT_LOG_DTL, otherwise the generated default note is kept.
        auditService.logTaskAssigned(pid, stage, department, candidateGroup,
                task.getId(), taskNoteOf(task), initiator);

        // then the notification e-mail: approval tasks notify their group /
        // single assignee, the initiator's own amendment task notifies
        // exactly the initiator that the request was rejected and returned
        if (TASK_AMEND.equals(task.getTaskDefinitionKey())) {
            notifyInitiatorOfAmendment(task, stage, initiator, pid);
        } else {
            notificationService.send(NotificationMessage.builder()
                    .type(NotificationMessage.Type.TASK_ASSIGNED)
                    .processKey(PROCESS_KEY)
                    .processName(PROCESS_NAME)
                    .processInstanceId(pid)
                    .stage(stage)
                    .department(department)
                    .candidateGroup(candidateGroup)
                    // null for group tasks; when the HOD / dean task was
                    // assigned to one person, the e-mail goes to exactly
                    // that person instead of every group member
                    .assigneeUser(task.getAssignee())
                    .taskId(task.getId())
                    .initiator(initiator)
                    .subject("[" + PROCESS_NAME + "] Approval required by " + safe(department))
                    .intro("A " + PROCESS_NAME + " approval task is waiting for your department.")
                    .additionalInfo("Please review and Approve / Reject the clearance request.")
                    .build());
        }
        log.debug("Clearance task {} created for stage {} / department {}",
                task.getId(), stage, department);
    }

    /**
     * The amendment task belongs to the initiator alone (BPMN
     * {@code flowable:assignee="${initiator}"}), so exactly that one person
     * is notified that a rejection returned the request for amendment.
     *
     * <p>The message deliberately carries <b>no</b> {@code candidateGroup} /
     * {@code department} - the initiator is a user, not a SIS group, so the
     * e-mail resolves through {@code recipientUser} (personal address from
     * {@code FLOWABLE_USERS_VW}), never through a group lookup.</p>
     */
    private void notifyInitiatorOfAmendment(DelegateTask task, String stage,
            String initiator, String pid) {
        String rejectedBy = str(task.getVariable(VAR_LAST_REJECTED_DEPARTMENT));
        String rejectionComment = str(task.getVariable(VAR_LAST_REJECTION_COMMENT));
        notificationService.send(NotificationMessage.builder()
                .type(NotificationMessage.Type.TASK_ASSIGNED)
                .processKey(PROCESS_KEY)
                .processName(PROCESS_NAME)
                .processInstanceId(pid)
                .stage(stage)
                .taskId(task.getId())
                .initiator(initiator)
                .recipientUser(initiator)
                .subject("[" + PROCESS_NAME + "] Action required: amend your clearance request")
                .intro("Your clearance request was rejected and returned to you for amendment.")
                .additionalInfo("Rejected by: " + safe(rejectedBy)
                        + (rejectionComment == null ? "" : " | Comment: " + rejectionComment)
                        + " | Please amend and resubmit your request.")
                .build());
    }

    // ------------------------------------------------------------------
    // complete: decision audit + department decision bookkeeping
    // ------------------------------------------------------------------

    private void onCompleted(DelegateTask task) {
        String stage = stageOf(task);
        String department = departmentOf(task, stage);
        String decision = str(task.getVariable(VAR_DECISION));
        String comment = str(task.getVariable(VAR_COMMENT));
        String completedBy = str(task.getVariable(VAR_COMPLETED_BY));
        if (completedBy == null) {
            completedBy = task.getAssignee();
        }
        String initiator = str(task.getVariable(VAR_INITIATOR));
        String pid = task.getProcessInstanceId();

        // amendment task completion is audited by ClearanceProcessHandler.recordAmendment
        if (decision == null) {
            return;
        }

        auditService.logTaskCompleted(pid, stage, department, completedBy,
                decision, comment, task.getId(), initiator);

        if (STAGE_DEPARTMENT_APPROVAL.equals(stage)) {
            int round = task.getVariable(VAR_APPROVAL_ROUND) instanceof Number n
                    ? n.intValue() : 1;

            Map<String, DepartmentDecision> decisions = decisionsOf(task);
            decisions.put(department, new DepartmentDecision(
                    department,
                    candidateGroupOf(task, stage, department),
                    decision,
                    completedBy,
                    comment,
                    LocalDateTime.now(),
                    round));

            boolean rejected = ClearanceConstants.DECISION_REJECT.equalsIgnoreCase(decision);
            if (rejected) {
                // Flag the rejection for the gatewayAllApproved gateway.
                // DelegateTask.setVariable propagates up to the process
                // instance scope. The multi-instance stage itself is a
                // SYNCHRONIZATION BARRIER (completion condition
                // nrOfCompletedInstances == nrOfInstances): a rejection
                // does NOT cancel sibling tasks - every department task
                // stays active until it is submitted, and only then does
                // the gateway route to the Amendment Task. This guarantees
                // the Amendment Task can never exist while a department
                // task is still pending.
                task.setVariable(VAR_ANY_DEPARTMENT_REJECTED, true);
            }
            task.setVariable(VAR_DEPARTMENT_DECISIONS, decisions);
            log.info("Clearance {} round {}: {} {} by {}",
                    pid, round, department, decision, completedBy);
        }

        // Future extension point: runs only after the Admission &
        // Registration submission itself was fully processed (decision
        // recorded + audit row written). Never called for department or
        // Finance completions, the amendment task, task creation or
        // assignment - and it fires again on every later round that
        // reaches Admission again after an amendment/resubmission.
        if (STAGE_ADMISSION_AND_REGISTRATION.equals(stage)) {
            handleAdmissionSubmission(task);
        }
    }

    // ------------------------------------------------------------------
    // post-Admission submission extension point (placeholder)
    // ------------------------------------------------------------------

    /**
     * Placeholder extension point invoked immediately after the Admission &
     * Registration approval task was completed/submitted and its decision
     * and audit row were recorded successfully - the final departmental
     * submission of the Clearance flow, both in the initial run and in
     * every round that reaches Admission again after amendment/resubmission.
     * Any decision (approve or reject) counts as a submission.
     *
     * <p>Intentionally empty: future post-Admission business logic (e.g.
     * downstream SIS processing) goes here. Do not move existing
     * completion/audit logic into this method, and keep the invocation at
     * the very end of {@link #onCompleted}.</p>
     *
     * <p>Protected so engine-level regression tests can observe the hook by
     * subclassing (see {@code ClearanceAdmissionSubmissionHookTest}).</p>
     *
     * @param task the completed Admission & Registration task - its
     *             variables (initiator, decision, comment, completedBy,
     *             ...) are still readable
     */
    protected void handleAdmissionSubmission(DelegateTask task) {
        // TODO: Implement post-Admission submission processing.
    }

    // ------------------------------------------------------------------
    // delete: audit cancellation of still-open department tasks
    // ------------------------------------------------------------------

    private void onDeleted(DelegateTask task) {
        // Completed tasks also fire DELETE - only audit the ones removed
        // while still open (e.g. process instance termination; the
        // multi-instance completion condition no longer cancels siblings).
        if (task.getVariable(VAR_DECISION) != null) {
            return;
        }
        String stage = stageOf(task);
        if (!STAGE_DEPARTMENT_APPROVAL.equals(stage)) {
            return;
        }
        String department = departmentOf(task, stage);
        auditService.logProcessAction(task.getProcessInstanceId(),
                ACTION_TASK_CANCELLED, stage, department, null,
                str(task.getVariable(VAR_INITIATOR)),
                "Task " + task.getId() + " cancelled before completion");
    }

    // ------------------------------------------------------------------
    // mapping helpers
    // ------------------------------------------------------------------

    /**
     * Assigns the HOD / dean department task to the single responsible
     * person resolved from the SIS (head of department / dean of college).
     * The candidate group stays on the task, so it remains visible in
     * every group-based view as well.
     */
    private void assignSingleApproverIfConfigured(DelegateTask task,
            String stage, String department) {
        if (approverResolver == null
                || !STAGE_DEPARTMENT_APPROVAL.equals(stage)
                || department == null) {
            return;
        }
        try {
            String assigneeId = approverResolver.resolveSingleApproverId(
                    department,
                    str(task.getVariable(VAR_STUDENT_FACULTY_NO)),
                    str(task.getVariable(VAR_STUDENT_DEPT_NO)),
                    str(task.getVariable(VAR_STUDENT_CAMPUS_NO)));
            if (assigneeId != null) {
                task.setAssignee(assigneeId);
                log.info("Clearance task {} (department {}) assigned to single approver {}",
                        task.getId(), department, assigneeId);
            }
        } catch (Exception e) {
            // resolution must never break task creation
            log.warn("Single-approver resolution failed for department {} - task stays "
                    + "a candidate-group task: {}", department, e.getMessage());
        }
    }

    private String stageOf(DelegateTask task) {
        switch (task.getTaskDefinitionKey() == null ? "" : task.getTaskDefinitionKey()) {
            case TASK_DEPARTMENT_APPROVAL:
                return STAGE_DEPARTMENT_APPROVAL;
            case TASK_FINANCE_APPROVAL:
                return STAGE_FINANCE;
            case TASK_ADMISSION_APPROVAL:
                return STAGE_ADMISSION_AND_REGISTRATION;
            case TASK_AMEND:
                return STAGE_AMENDMENT;
            default:
                return task.getTaskDefinitionKey();
        }
    }

    /**
     * Department display name. For the multi-instance department task the
     * element variable {@code department} holds it; for the sequential
     * stages the fixed group is returned.
     */
    private String departmentOf(DelegateTask task, String stage) {
        if (STAGE_DEPARTMENT_APPROVAL.equals(stage)) {
            return str(task.getVariable(VAR_DEPARTMENT));
        }
        if (STAGE_FINANCE.equals(stage)) {
            return GROUP_FINANCE;
        }
        if (STAGE_ADMISSION_AND_REGISTRATION.equals(stage)) {
            return GROUP_ADMISSION_AND_REGISTRATION;
        }
        return str(task.getVariable(VAR_INITIATOR));
    }

    /**
     * Candidate group of the task as the <b>SIS group id</b>
     * ({@code ROLE_CODE_}) that {@code FLOWABLE_USERS_VW} /
     * {@code flowable_groups_vw} can resolve members for. Department and
     * group share the same identifier in the departmental stage (the
     * resolver returns role codes the BPMN uses directly as candidate
     * groups), but the sequential Finance / Admission stages carry display
     * names as department - those must be translated back to their role
     * codes ({@code FIN} / {@code REG}), otherwise the group resolves to
     * zero members and the notification is silently skipped. (Flowable 7
     * removed {@code DelegateTask#getCandidateGroups()}.)
     */
    private String candidateGroupOf(DelegateTask task, String stage, String department) {
        if (STAGE_FINANCE.equals(stage)) {
            return GROUP_FINANCE_ROLE_CODE;
        }
        if (STAGE_ADMISSION_AND_REGISTRATION.equals(stage)) {
            return GROUP_ADMISSION_AND_REGISTRATION_ROLE_CODE;
        }
        return department;
    }

    @SuppressWarnings("unchecked")
    private Map<String, DepartmentDecision> decisionsOf(DelegateTask task) {
        Object value = task.getVariable(VAR_DEPARTMENT_DECISIONS);
        return value instanceof Map ? (Map<String, DepartmentDecision>) value : new java.util.LinkedHashMap<>();
    }

    private String str(Object value) {
        return value != null ? value.toString() : null;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    /**
     * Optional free text note persisted on the task. Probes the task local
     * variables note / notes first, then the task description; returns null
     * when nothing was persisted (the audit row then falls back to its
     * generated default note).
     */
    private String taskNoteOf(DelegateTask task) {
        for (String name : new String[]{"note", "notes"}) {
            String value = str(task.getVariableLocal(name));
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return str(task.getDescription());
    }

}
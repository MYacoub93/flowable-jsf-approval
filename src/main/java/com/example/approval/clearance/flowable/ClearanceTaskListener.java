package com.example.approval.clearance.flowable;

import com.example.approval.clearance.ClearanceConstants;
import com.example.approval.clearance.model.DepartmentDecision;
import com.example.approval.audit.service.BpmAuditService;
import com.example.approval.notification.model.NotificationMessage;
import com.example.approval.notification.service.NotificationService;
import org.flowable.engine.delegate.TaskListener;
import org.flowable.task.service.delegate.DelegateTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

import static com.example.approval.clearance.ClearanceConstants.*;

/**
 * The one reusable TaskListener attached to <b>every</b> approval task of the
 * Clearance Letter process (department multi-instance tasks, Finance,
 * Admission and the initiator amendment task):
 *
 * <ul>
 *   <li><b>create</b> - sends the "task is waiting" e-mail via
 *       {@link NotificationService} and writes the {@code TASK_ASSIGNED}
 *       audit row via {@link BpmAuditService};</li>
 *   <li><b>complete</b> - writes the {@code APPROVED}/{@code REJECTED} audit
 *       row, stores the {@link DepartmentDecision} in the
 *       {@code departmentDecisions} map and raises
 *       {@code anyDepartmentRejected} as soon as one department rejects
 *       (this is what the multi-instance completion condition reacts to);</li>
 *   <li><b>delete</b> - writes a {@code TASK_CANCELLED} audit row when the
 *       multi-instance completion condition removes not-yet-completed
 *       sibling tasks.</li>
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

    public ClearanceTaskListener(NotificationService notificationService,
            BpmAuditService auditService) {
        this.notificationService = notificationService;
        this.auditService = auditService;
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
        String candidateGroup = candidateGroupOf(task, department);
        String initiator = str(task.getVariable(VAR_INITIATOR));
        String pid = task.getProcessInstanceId();

        // audit first: TASK_ASSIGNED for every task handed to an approver
        auditService.logTaskAssigned(pid, stage, department, candidateGroup,
                task.getId(), initiator);

        // then the notification e-mail (only for approval tasks, not for the
        // initiator's own amendment/result tasks)
        boolean notifyApprover = !TASK_AMEND.equals(task.getTaskDefinitionKey());
        if (notifyApprover) {
            notificationService.send(NotificationMessage.builder()
                    .type(NotificationMessage.Type.TASK_ASSIGNED)
                    .processKey(PROCESS_KEY)
                    .processName(PROCESS_NAME)
                    .processInstanceId(pid)
                    .stage(stage)
                    .department(department)
                    .candidateGroup(candidateGroup)
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
                    candidateGroupOf(task, department),
                    decision,
                    completedBy,
                    comment,
                    LocalDateTime.now(),
                    round));

            boolean rejected = ClearanceConstants.DECISION_REJECT.equalsIgnoreCase(decision);
            if (rejected) {
                // visible to the multi-instance completion condition
                // -> remaining sibling tasks are cancelled.
                // DelegateTask.setVariable propagates up to the process
                // instance scope, which is where the completion condition
                // reads it.
                task.setVariable(VAR_ANY_DEPARTMENT_REJECTED, true);
            }
            task.setVariable(VAR_DEPARTMENT_DECISIONS, decisions);
            log.info("Clearance {} round {}: {} {} by {}",
                    pid, round, department, decision, completedBy);
        }
    }

    // ------------------------------------------------------------------
    // delete: audit cancellation of still-open sibling tasks
    // ------------------------------------------------------------------

    private void onDeleted(DelegateTask task) {
        // Completed tasks also fire DELETE - only audit the ones removed
        // while still open (multi-instance completion condition).
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
                "Task " + task.getId() + " cancelled - another department already rejected");
    }

    // ------------------------------------------------------------------
    // mapping helpers
    // ------------------------------------------------------------------

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
     * Candidate group of the task. Department and group share the same
     * identifier in the Clearance process: the resolver returns group ids
     * that the BPMN uses directly as candidate groups, so the department
     * name is the group. (Flowable 7 removed
     * {@code DelegateTask#getCandidateGroups()}.)
     */
    private String candidateGroupOf(DelegateTask task, String department) {
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
}
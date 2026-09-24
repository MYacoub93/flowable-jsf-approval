package com.example.approval.processes.withdrawal.flowable;

import com.example.approval.processes.withdrawal.model.WithdrawalApprovalResult;
import com.example.approval.audit.service.BpmAuditService;
import com.example.approval.notification.model.NotificationMessage;
import com.example.approval.notification.service.NotificationService;
import com.example.approval.processes.withdrawal.service.WithdrawalApproverResolverService;
import org.flowable.engine.delegate.TaskListener;
import org.flowable.task.service.delegate.DelegateTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.example.approval.processes.withdrawal.SemesterWithdrawalConstants.*;

/**
 * The one reusable TaskListener attached to <b>every</b> task of the
 * Semester Withdrawal process ({@code semester-withdrawl}) - the exact
 * mirror of {@code ClearanceTaskListener}:
 *
 * <ul>
 *   <li><b>create</b> - writes the {@code TASK_ASSIGNED} (RECEIVED) audit
 *       row and sends the "task is waiting" e-mail via
 *       {@link NotificationService}. The dean parallel task is dynamically
 *       assigned to the single dean resolved from the SIS
 *       ({@code getDeanOfCollage}); the amendment / result tasks mail the
 *       initiator directly;</li>
 *   <li><b>complete</b> - writes the {@code APPROVED}/{@code REJECTED}
 *       audit row (rejection reason preserved in the note), stores the
 *       {@link WithdrawalApprovalResult} in the {@code approvalResults}
 *       map and raises {@code anyApprovalRejected} as soon as one party
 *       rejects (the flag is consumed by the gateway <b>after</b> the
 *       multi-instance synchronization barrier released - it does NOT
 *       cancel sibling tasks: every applicable party must submit before
 *       the stage is left);</li>
 *   <li><b>delete</b> - writes a {@code TASK_CANCELLED} audit row if a
 *       parallel approval task is ever removed while still open.</li>
 * </ul>
 *
 * <p>Attached once per task in the BPMN via
 * {@code delegateExpression="${withdrawalTaskListener}"} - no duplicated
 * listener code anywhere in the model.</p>
 */
@Component("withdrawalTaskListener")
public class WithdrawalTaskListener implements TaskListener {

    private static final long serialVersionUID = 1L;

    private static final Logger log = LoggerFactory.getLogger(WithdrawalTaskListener.class);

    private final NotificationService notificationService;
    private final BpmAuditService auditService;
    private final WithdrawalApproverResolverService approverResolver;

    /**
     * Production constructor (Spring): the approver resolver assigns the
     * dean task directly to one specific person. {@code @Autowired} is
     * required because the class also exposes a 2-arg test constructor.
     */
    @Autowired
    public WithdrawalTaskListener(NotificationService notificationService,
            BpmAuditService auditService,
            WithdrawalApproverResolverService approverResolver) {
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.approverResolver = approverResolver;
    }

    /** Test/fallback constructor: dean tasks stay candidate-group tasks. */
    public WithdrawalTaskListener(NotificationService notificationService,
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
    // create: e-mail + TASK_ASSIGNED (RECEIVED) audit
    // ------------------------------------------------------------------

    private void onCreated(DelegateTask task) {
        String stage = stageOf(task);
        String department = departmentOf(task, stage);
        String candidateGroup = candidateGroupOf(task, stage);
        String initiator = str(task.getVariable(VAR_INITIATOR));
        String pid = task.getProcessInstanceId();

        // Dean task goes to ONE specific person instead of only the whole
        // candidate group. Runs first so the audit row and the e-mail see
        // the final assignee. Best effort: if no dean can be resolved the
        // task simply remains a group task - creation never fails.
        assignDeanIfConfigured(task, stage);

        auditService.logTaskAssigned(pid, stage, department, candidateGroup,
                task.getId(), taskNoteOf(task), initiator);

        if (TASK_AMENDMENT.equals(task.getTaskDefinitionKey())) {
            notifyInitiatorOfAmendment(task, stage, initiator, pid);
        } else if (TASK_STUDENT_RESULT.equals(task.getTaskDefinitionKey())) {
            notifyInitiatorOfResult(task, stage, initiator, pid);
        } else {
            notificationService.send(NotificationMessage.builder()
                    .type(NotificationMessage.Type.TASK_ASSIGNED)
                    .processKey(PROCESS_KEY)
                    .processName(PROCESS_NAME)
                    .processInstanceId(pid)
                    .stage(stage)
                    .department(department)
                    .candidateGroup(candidateGroup)
                    // null for group tasks; when the dean task was assigned
                    // to one person, the e-mail goes to exactly that person
                    .assigneeUser(task.getAssignee())
                    .taskId(task.getId())
                    .initiator(initiator)
                    .subject("[" + PROCESS_NAME + "] Approval required by " + safe(department))
                    .intro("A " + PROCESS_NAME + " approval task is waiting for your review.")
                    .additionalInfo("Please review and Approve / Reject the withdrawal request "
                            + "of semester " + safe(str(task.getVariable(VAR_WITHDRAWAL_SEMESTER))) + ".")
                    .build());
        }
        log.debug("Withdrawal task {} created for stage {} / department {}",
                task.getId(), stage, department);
    }

    /**
     * The amendment task belongs to the initiator alone (BPMN
     * {@code flowable:assignee="${initiator}"}), so exactly that one person
     * is notified that a rejection returned the request for amendment.
     */
    private void notifyInitiatorOfAmendment(DelegateTask task, String stage,
            String initiator, String pid) {
        String rejectedBy = str(task.getVariable(VAR_LAST_REJECTED_PARTY));
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
                .subject("[" + PROCESS_NAME + "] Action required: amend your withdrawal request")
                .intro("Your semester withdrawal request was rejected and returned to you "
                        + "for amendment.")
                .additionalInfo("Rejected by: " + safe(rejectedBy)
                        + (rejectionComment == null ? "" : " | Comment: " + rejectionComment)
                        + " | Please amend and resubmit your request.")
                .build());
    }

    /**
     * The final student result task (success) also belongs to the
     * initiator alone - the e-mail resolves through {@code recipientUser}.
     */
    private void notifyInitiatorOfResult(DelegateTask task, String stage,
            String initiator, String pid) {
        notificationService.send(NotificationMessage.builder()
                .type(NotificationMessage.Type.RESULT)
                .processKey(PROCESS_KEY)
                .processName(PROCESS_NAME)
                .processInstanceId(pid)
                .stage(stage)
                .taskId(task.getId())
                .initiator(initiator)
                .recipientUser(initiator)
                .subject("[" + PROCESS_NAME + "] Your semester withdrawal was executed")
                .intro("All approvals completed and your semester withdrawal was executed "
                        + "successfully in SIS.")
                .additionalInfo("Semester: "
                        + safe(str(task.getVariable(VAR_WITHDRAWAL_SEMESTER_DESC)))
                        + " | SIS message: "
                        + safe(str(task.getVariable(VAR_WITHDRAWAL_MESSAGE))))
                .build());
    }

    // ------------------------------------------------------------------
    // complete: decision audit + approval-result bookkeeping
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

        // amendment / result task completion carries no decision variable
        if (decision == null) {
            return;
        }

        auditService.logTaskCompleted(pid, stage, department, completedBy,
                decision, comment, task.getId(), initiator);

        if (STAGE_PARALLEL_APPROVAL.equals(stage)) {
            int round = task.getVariable(VAR_APPROVAL_ROUND) instanceof Number n
                    ? n.intValue() : 1;

            Map<String, WithdrawalApprovalResult> decisions = decisionsOf(task);
            String party = str(task.getVariable(VAR_APPROVAL_GROUP));
            decisions.put(party, new WithdrawalApprovalResult(
                    party,
                    partyNameOf(party),
                    candidateGroupOf(task, stage),
                    decision,
                    completedBy,
                    comment,
                    LocalDateTime.now()));

            boolean rejected = DECISION_REJECT.equalsIgnoreCase(decision);
            if (rejected) {
                // Flag the rejection for the gateway AFTER the barrier. The
                // multi-instance stage itself is a SYNCHRONIZATION BARRIER
                // (completion condition nrOfCompletedInstances ==
                // nrOfInstances): a rejection does NOT cancel sibling tasks
                // - every applicable parallel task stays active until it is
                // submitted, and only then does the gateway route to the
                // Amendment Task. This guarantees the Amendment Task can
                // never exist while a parallel approval task is pending.
                task.setVariable(VAR_ANY_APPROVAL_REJECTED, true);
            }
            task.setVariable(VAR_APPROVAL_RESULTS, decisions);
            log.info("Withdrawal {} round {}: {} {} by {}",
                    pid, round, party, decision, completedBy);
        }

        // REG approval: store the sequential-stage decision variables for
        // the audit trail / final result screen
        if (STAGE_ADMISSION_AND_REGISTRATION.equals(stage)) {
            task.setVariable(VAR_REG_DECISION, decision);
            task.setVariable(VAR_REG_COMMENT, comment);
        }
        if (STAGE_FINANCE.equals(stage)) {
            task.setVariable(VAR_FIN_DECISION, decision);
            task.setVariable(VAR_FIN_COMMENT, comment);
        }
    }

    // ------------------------------------------------------------------
    // delete: audit cancellation of still-open parallel tasks
    // ------------------------------------------------------------------

    private void onDeleted(DelegateTask task) {
        // Completed tasks also fire DELETE - only audit the ones removed
        // while still open (e.g. process instance termination).
        if (task.getVariable(VAR_DECISION) != null) {
            return;
        }
        String stage = stageOf(task);
        if (!STAGE_PARALLEL_APPROVAL.equals(stage)) {
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
     * Assigns the dean parallel task to the single dean of the student's
     * college resolved from the SIS
     * ({@code CommonMapper.getDeanOfCollage} -> Flowable user). The
     * candidate group stays on the task, so it remains visible in every
     * group-based view as well.
     */
    private void assignDeanIfConfigured(DelegateTask task, String stage) {
        if (approverResolver == null
                || !STAGE_PARALLEL_APPROVAL.equals(stage)
                || !GROUP_DEAN.equals(str(task.getVariable(VAR_APPROVAL_GROUP)))) {
            return;
        }
        try {
            String assigneeId = approverResolver.resolveDeanUserId(
                    str(task.getVariable(VAR_FACULTY_NO)),
                    str(task.getVariable(VAR_CAMPUS_NO)));
            if (assigneeId != null) {
                task.setAssignee(assigneeId);
                task.setVariable(VAR_DEAN_USER, assigneeId);
                log.info("Withdrawal dean task {} assigned to single approver {}",
                        task.getId(), assigneeId);
            }
        } catch (Exception e) {
            // resolution must never break task creation
            log.warn("Dean resolution failed - task stays a candidate-group task: {}",
                    e.getMessage());
        }
    }

    private String stageOf(DelegateTask task) {
        switch (task.getTaskDefinitionKey() == null ? "" : task.getTaskDefinitionKey()) {
            case TASK_REG_APPROVAL:
            case TASK_REG_FYI:
                return STAGE_ADMISSION_AND_REGISTRATION;
            case TASK_PARALLEL_APPROVAL:
                return STAGE_PARALLEL_APPROVAL;
            case TASK_AMENDMENT:
                return STAGE_AMENDMENT;
            case TASK_FINANCE_APPROVAL:
                return STAGE_FINANCE;
            case TASK_STUDENT_RESULT:
                return STAGE_STUDENT_RESULT;
            default:
                return task.getTaskDefinitionKey();
        }
    }

    /**
     * Department display name for the audit trail. For the multi-instance
     * parallel task the element variable {@code approvalGroup} holds the
     * party id; the sequential stages carry their fixed groups.
     */
    private String departmentOf(DelegateTask task, String stage) {
        if (STAGE_PARALLEL_APPROVAL.equals(stage)) {
            return partyNameOf(str(task.getVariable(VAR_APPROVAL_GROUP)));
        }
        if (STAGE_FINANCE.equals(stage)) {
            return DEPT_FINANCE;
        }
        if (STAGE_ADMISSION_AND_REGISTRATION.equals(stage)) {
            return DEPT_ADMISSION_AND_REGISTRATION;
        }
        return str(task.getVariable(VAR_INITIATOR));
    }

    /**
     * Candidate group of the task as the <b>SIS role code</b> that
     * {@code FLOWABLE_USERS_VW} resolves members for (Flowable 7 removed
     * {@code DelegateTask#getCandidateGroups()}).
     */
    private String candidateGroupOf(DelegateTask task, String stage) {
        if (STAGE_PARALLEL_APPROVAL.equals(stage)) {
            return str(task.getVariable(VAR_APPROVAL_GROUP));
        }
        if (STAGE_FINANCE.equals(stage)) {
            return GROUP_FINANCE;
        }
        if (STAGE_ADMISSION_AND_REGISTRATION.equals(stage)) {
            return GROUP_ADMISSION_AND_REGISTRATION;
        }
        return null;
    }

    public static String partyNameOf(String party) {
        if (party == null) {
            return null;
        }
        return switch (party) {
            case GROUP_DEAN -> DEPT_DEAN;
            case GROUP_STUDENT_AFFAIRS -> DEPT_STUDENT_AFFAIRS;
            case GROUP_LIBRARY -> DEPT_LIBRARY;
            case GROUP_INTERNAL_HOUSING -> DEPT_INTERNAL_HOUSING;
            case GROUP_HEALTH_CARE -> DEPT_HEALTH_CARE;
            case GROUP_FINANCE -> DEPT_FINANCE;
            case GROUP_ADMISSION_AND_REGISTRATION -> DEPT_ADMISSION_AND_REGISTRATION;
            default -> party;
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, WithdrawalApprovalResult> decisionsOf(DelegateTask task) {
        Object value = task.getVariable(VAR_APPROVAL_RESULTS);
        return value instanceof Map ? (Map<String, WithdrawalApprovalResult>) value
                : new LinkedHashMap<>();
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
     * when nothing was persisted.
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
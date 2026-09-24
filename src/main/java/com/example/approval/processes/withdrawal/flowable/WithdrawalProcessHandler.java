package com.example.approval.processes.withdrawal.flowable;

import com.example.approval.audit.BpmAuditConstants;
import com.example.approval.audit.service.BpmAuditService;
import com.example.approval.notification.model.NotificationMessage;
import com.example.approval.notification.service.NotificationService;
import com.example.approval.processes.withdrawal.model.WithdrawalApprovalResult;
import com.example.approval.service.CommonService;
import org.flowable.engine.TaskService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.task.api.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.example.approval.processes.withdrawal.SemesterWithdrawalConstants.*;

/**
 * Flowable service-task delegates of the Semester Withdrawal process
 * ({@code semester-withdrawl}) - the exact mirror of
 * {@code ClearanceProcessHandler}:
 *
 * <ul>
 *   <li>{@link #resolveApprovers} - builds the dynamic parallel approver list
 *       (Dean of College, Student Affairs, Library, Health Care and -
 *       female students only - Internal Housing) minus every party that
 *       already approved in an earlier round (cumulative resubmission
 *       rule of the Clearance process);</li>
 *   <li>{@link #evaluateParallelStage} - after the multi-instance
 *       synchronization barrier: carries approved parties over into
 *       {@code approvedParties} and copies the first rejection into the
 *       {@code lastRejected*} variables for the amendment form;</li>
 *   <li>{@link #recordStageRejection} - REG / FIN rejection bookkeeping;</li>
 *   <li>{@link #recordAmendment} - audit row + notification bookkeeping when
 *       the student resubmits an amended request;</li>
 *   <li>{@link #executeWithdrawal} - calls the SIS procedure
 *       {@code bpm_pkg.withdrawal_student_semester} and evaluates its
 *       {@code result} OUT parameter (never assumes success);</li>
 *   <li>{@link #completeWithdrawalFailure} - auditable standalone failure
 *       notice for the student when SIS reported a failure.</li>
 * </ul>
 */
@Component("withdrawalProcessHandler")
public class WithdrawalProcessHandler {

    private static final Logger log = LoggerFactory.getLogger(WithdrawalProcessHandler.class);

    private final CommonService commonService;
    private final NotificationService notificationService;
    private final BpmAuditService auditService;
    private final TaskService taskService;

    /**
     * Production constructor (Spring). {@code @Autowired} is required because
     * the class also exposes a 3-arg test constructor.
     */
    @Autowired
    public WithdrawalProcessHandler(CommonService commonService,
            NotificationService notificationService,
            BpmAuditService auditService,
            TaskService taskService) {
        this.commonService = commonService;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.taskService = taskService;
    }

    /** Test/fallback constructor without a TaskService (no standalone tasks). */
    public WithdrawalProcessHandler(CommonService commonService,
            NotificationService notificationService,
            BpmAuditService auditService) {
        this(commonService, notificationService, auditService, null);
    }

    // ------------------------------------------------------------------
    // start listener hook (audit master row is opened by openCase)
    // ------------------------------------------------------------------

    public void processStarted(DelegateExecution execution) {
        log.info("Semester Withdrawal {} started by {}",
                execution.getProcessInstanceId(), var(execution, VAR_INITIATOR));
    }

    // ------------------------------------------------------------------
    // Dynamic parallel approver resolution (before every approval round)
    // ------------------------------------------------------------------

    /**
     * Service task "Resolve Parallel Approvers". Runs initially and after
     * every amendment. The full approver set is Dean of College (DEN),
     * Student Affairs (STD_AFF), Library (LIB) and Health Care (HC) plus
     * Internal Housing (Housing) <b>only for female students</b> - the
     * gender comes from the read-only SIS student snapshot stored at start.
     *
     * <p><b>Resubmission rule (Clearance pattern):</b> parties that already
     * APPROVED in an earlier round (cumulative {@code approvedParties}) are
     * filtered out and never asked twice; rejected / not-yet-reached parties
     * circulate again.</p>
     */
    public void resolveApprovers(DelegateExecution execution) {
        List<String> fullSet = new ArrayList<>(List.of(
                GROUP_DEAN, GROUP_STUDENT_AFFAIRS, GROUP_LIBRARY, GROUP_HEALTH_CARE));
        if (isFemale(execution)) {
            fullSet.add(GROUP_INTERNAL_HOUSING);
        }

        List<String> alreadyApproved = approvedParties(execution);
        List<String> pending = new ArrayList<>();
        for (String party : fullSet) {
            if (!alreadyApproved.contains(party)) {
                pending.add(party);
            }
        }

        int round = roundOf(execution) + 1;
        execution.setVariable(VAR_PARALLEL_APPROVERS, pending);
        execution.setVariable(VAR_ANY_APPROVAL_REJECTED, false);
        execution.setVariable(VAR_APPROVAL_RESULTS, new LinkedHashMap<String, WithdrawalApprovalResult>());
        execution.setVariable(VAR_APPROVED_PARTIES, alreadyApproved);
        execution.setVariable(VAR_APPROVAL_ROUND, round);

        log.info("Withdrawal {}: round {} parallel approvers = {} (already approved: {}, female={})",
                execution.getProcessInstanceId(), round, pending, alreadyApproved,
                isFemale(execution));
    }

    /**
     * Service task after the multi-instance synchronization barrier
     * ({@code nrOfCompletedInstances == nrOfInstances}). Carries every
     * approver that approved in this round over into the cumulative
     * {@code approvedParties} variable and copies the first rejection into
     * the {@code lastRejected*} variables shown on the amendment form.
     */
    public void evaluateParallelStage(DelegateExecution execution) {
        Map<String, WithdrawalApprovalResult> decisions = decisions(execution);

        List<String> approved = approvedParties(execution);
        decisions.values().stream()
                .filter(WithdrawalApprovalResult::isApproved)
                .map(WithdrawalApprovalResult::getParty)
                .filter(party -> !approved.contains(party))
                .forEach(approved::add);
        execution.setVariable(VAR_APPROVED_PARTIES, approved);

        WithdrawalApprovalResult rejection = decisions.values().stream()
                .filter(d -> !d.isApproved())
                .findFirst()
                .orElse(null);
        if (rejection != null) {
            execution.setVariable(VAR_LAST_REJECTED_STAGE, STAGE_PARALLEL_APPROVAL);
            execution.setVariable(VAR_LAST_REJECTED_PARTY, rejection.getPartyName());
            execution.setVariable(VAR_LAST_REJECTION_COMMENT, rejection.getComment());
        }
        log.info("Withdrawal {}: parallel stage evaluated - approved so far {}, rejected={}",
                execution.getProcessInstanceId(), approved,
                rejection != null ? rejection.getParty() : "none");
    }

    /**
     * Service task after a REG / FIN rejection. Called with literals:
     * {@code ${withdrawalProcessHandler.recordStageRejection(execution,
     * 'FINANCE', 'Financial Department')}}.
     */
    public void recordStageRejection(DelegateExecution execution, String stage, String party) {
        execution.setVariable(VAR_LAST_REJECTED_STAGE, stage);
        execution.setVariable(VAR_LAST_REJECTED_PARTY, party);
        String comment = var(execution, VAR_COMMENT);
        if (comment != null) {
            execution.setVariable(VAR_LAST_REJECTION_COMMENT, comment);
        }
        log.info("Withdrawal {}: rejected at stage {} by {}",
                execution.getProcessInstanceId(), stage, party);
    }

    /**
     * Service task after the student completed the amendment task: writes
     * the REQUEST_AMENDED audit row (with the student's amendment notes and
     * the previous rejection details) - mirrors
     * {@code ClearanceProcessHandler.recordAmendment}.
     */
    public void recordAmendment(DelegateExecution execution) {
        String initiator = var(execution, VAR_INITIATOR);
        String pid = execution.getProcessInstanceId();
        String amendmentNotes = var(execution, VAR_AMENDMENT_NOTES);
        String rejectedBy = var(execution, VAR_LAST_REJECTED_PARTY);
        String rejectionComment = var(execution, VAR_LAST_REJECTION_COMMENT);

        auditService.logProcessAction(pid, BpmAuditConstants.ACTION_REQUEST_AMENDED,
                STAGE_AMENDMENT, null, initiator, initiator,
                "Request amended" + (amendmentNotes == null || amendmentNotes.isBlank()
                        ? "" : ": " + amendmentNotes)
                        + " | previously rejected by: " + safe(rejectedBy)
                        + (rejectionComment == null ? "" : " (" + rejectionComment + ")"));
        log.info("Withdrawal {} amended by {}", pid, initiator);
    }

    // ------------------------------------------------------------------
    // SIS withdrawal execution
    // ------------------------------------------------------------------

    /**
     * Service task "Execute Semester Withdrawal" - calls the existing SIS
     * procedure wrapper {@code CommonService.processSemesterWithdrawal}
     * ({@code bpm_pkg.withdrawal_student_semester}) with the student id,
     * the chosen semester and the language, then evaluates the returned
     * {@code result} OUT parameter. <b>The DB call returning without an
     * exception is NOT treated as success</b>: only {@code result == 1}
     * sets {@code withdrawalResult} to 1; anything else keeps the failure
     * code and message, is audited via the shared
     * {@code TRANSACTION_SERVICE_FAILURE} action and routes the process to
     * the failure notice instead of the student success task.
     *
     * <p>SIS infrastructure failures are caught and recorded the same way -
     * per project convention they must not break the Flowable transaction
     * in a way that hides the failure from the audit trail.</p>
     */
    public void executeWithdrawal(DelegateExecution execution) {
        String pid = execution.getProcessInstanceId();
        String initiator = var(execution, VAR_INITIATOR);
        String studentId = var(execution, VAR_STUDENT_ID);
        String semester = var(execution, VAR_WITHDRAWAL_SEMESTER);
        String lang = langOf(execution);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("studentId", studentId);
        params.put("semester", semester);
        params.put("lang", lang);

        int result;
        String message;
        try {
            Map<String, Object> out = commonService.processSemesterWithdrawal(params);
            result = number(out != null ? out.get("result") : null, 0);
            Object msg = out != null ? out.get("message") : null;
            message = msg != null ? msg.toString() : null;
        } catch (Exception e) {
            log.error("Withdrawal {}: SIS withdrawal procedure call failed", pid, e);
            result = 0;
            message = "SIS withdrawal procedure failed: " + e.getMessage();
        }

        execution.setVariable(VAR_WITHDRAWAL_RESULT, result);
        execution.setVariable(VAR_WITHDRAWAL_MESSAGE, message);
        log.info("Withdrawal {}: SIS withdrawal result={} message={}", pid, result, message);

        if (result != 1) {
            auditService.logProcessAction(pid,
                    BpmAuditConstants.ACTION_TRANSACTION_SERVICE_FAILURE,
                    STAGE_WITHDRAWAL_EXECUTION, null, initiator, initiator,
                    "bpm_pkg.withdrawal_student_semester failed for student " + studentId
                            + " semester " + semester + ": " + safe(message));
        }
    }

    /**
     * Service task after a FAILED SIS withdrawal: creates a standalone
     * (non-blocking) failure notice task for the student - the same pattern
     * as {@code ClearanceProcessHandler.completeClearance} - so the failure
     * is visible in the portal, e-mailed to the initiator and audited, and
     * the process then ends without ever showing a false success.
     */
    public void completeWithdrawalFailure(DelegateExecution execution) {
        String initiator = var(execution, VAR_INITIATOR);
        String pid = execution.getProcessInstanceId();
        String message = var(execution, VAR_WITHDRAWAL_MESSAGE);

        auditService.logProcessAction(pid, BpmAuditConstants.ACTION_PROCESS_COMPLETED,
                STAGE_WITHDRAWAL_EXECUTION, null, initiator, initiator,
                "Semester Withdrawal: FAILED - " + safe(message));

        notificationService.send(NotificationMessage.builder()
                .type(NotificationMessage.Type.RESULT)
                .processKey(PROCESS_KEY)
                .processName(PROCESS_NAME)
                .processInstanceId(pid)
                .recipientUser(initiator)
                .initiator(initiator)
                .subject("[" + PROCESS_NAME + "] Withdrawal NOT executed")
                .intro("Your semester withdrawal request could not be executed in SIS.")
                .additionalInfo(safe(message) + " | Please contact Admission and Registration.")
                .build());

        if (taskService != null) {
            Task failureTask = taskService.newTask();
            failureTask.setName("Semester Withdrawal: Failed");
            failureTask.setCategory(CATEGORY_RESULT);
            failureTask.setDescription("The SIS semester withdrawal could not be executed. "
                    + "Reason: " + safe(message) + " | Process instance: " + pid);
            failureTask.setAssignee(initiator);
            taskService.saveTask(failureTask);
            taskService.setVariable(failureTask.getId(), "processInstanceId", pid);
            taskService.setVariable(failureTask.getId(), VAR_WITHDRAWAL_RESULT, 0);
            taskService.setVariable(failureTask.getId(), VAR_WITHDRAWAL_MESSAGE, message);
            auditService.logProcessAction(pid, BpmAuditConstants.ACTION_TASK_ASSIGNED,
                    STAGE_STUDENT_RESULT, null, initiator, initiator,
                    "Failure notice task " + failureTask.getId() + " created for initiator");
        }
        log.warn("Withdrawal {} finished with SIS failure - failure notice created", pid);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private boolean isFemale(DelegateExecution execution) {
        String gender = var(execution, VAR_GENDER);
        if (gender == null) {
            return false;
        }
        String g = gender.trim();
        // SIS gender codes: 'F' female / 'M' male (numeric variant 2 = female)
        return "F".equalsIgnoreCase(g) || "2".equals(g) || "female".equalsIgnoreCase(g);
    }

    @SuppressWarnings("unchecked")
    private List<String> approvedParties(DelegateExecution execution) {
        Object value = execution.getVariable(VAR_APPROVED_PARTIES);
        return value instanceof List ? new ArrayList<>((List<String>) value) : new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private Map<String, WithdrawalApprovalResult> decisions(DelegateExecution execution) {
        Object value = execution.getVariable(VAR_APPROVAL_RESULTS);
        return value instanceof Map ? (Map<String, WithdrawalApprovalResult>) value : new LinkedHashMap<>();
    }

    private int roundOf(DelegateExecution execution) {
        Object value = execution.getVariable(VAR_APPROVAL_ROUND);
        return value instanceof Number n ? n.intValue() : 0;
    }

    private String langOf(DelegateExecution execution) {
        String lang = var(execution, VAR_LANG);
        return lang != null ? lang : "2";
    }

    private int number(Object value, int fallback) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private String var(DelegateExecution execution, String name) {
        Object value = execution.getVariable(name);
        return value != null ? value.toString() : null;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
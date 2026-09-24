package com.example.approval.processes.withdrawal.service;

import com.example.approval.audit.service.BpmAuditService;
import com.example.approval.origin.beans.StudentInfoBean;
import com.example.approval.processes.BPM_constants;
import com.example.approval.processes.withdrawal.flowable.WithdrawalTaskListener;
import com.example.approval.service.CommonService;
import com.example.approval.service.ProcessStartService;
import org.flowable.engine.IdentityService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.idm.api.Group;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.example.approval.processes.withdrawal.SemesterWithdrawalConstants.*;

/**
 * Facade used by the JSF layer for everything Semester Withdrawal specific:
 * start authorization (STD), SIS presubmit validation <b>before</b> the
 * process instance is created, combined assignee/candidate-group task
 * inbox, claim + complete with the decision variables, amendment
 * completion and acknowledgement of the final student result task.
 *
 * <p>All process-agnostic plumbing (authenticating the initiator user
 * before {@code startProcessInstanceByKey}) stays in
 * {@link ProcessStartService}.</p>
 */
@Service("semesterWithdrawalService")
@Transactional
public class SemesterWithdrawalService {

    private static final Logger log = LoggerFactory.getLogger(SemesterWithdrawalService.class);

    /**
     * Presubmit document code of the semester-withdrawl transaction in SIS
     * ({@code BPM_PKG.check_presubmit_bpm_service} parameter
     * {@code documentCode}). The value is centralized in
     * {@code BPM_constants.Withdrawl_Process_DOCUMENT_CODE} - the same
     * convention as the existing student proof / clearance processes: the
     * document code identifies the transaction type being validated.
     */
    public static final int PRESUBMIT_DOCUMENT_CODE =
            BPM_constants.Withdrawl_Process_DOCUMENT_CODE;

    /** SIS presubmit validation success status ({@code status == 1}). */
    public static final int PRESUBMIT_STATUS_OK = 1;

    /** SIS procedure success result ({@code result == 1}). */
    public static final int WITHDRAWAL_RESULT_OK = 1;

    private final ProcessStartService processStartService;
    private final IdentityService identityService;
    private final TaskService taskService;
    private final RuntimeService runtimeService;
    private final BpmAuditService auditService;
    private final CommonService commonService;

    public SemesterWithdrawalService(ProcessStartService processStartService,
                                     IdentityService identityService,
                                     TaskService taskService,
                                     RuntimeService runtimeService,
                                     BpmAuditService auditService,
                                     CommonService commonService) {
        this.processStartService = processStartService;
        this.identityService = identityService;
        this.taskService = taskService;
        this.runtimeService = runtimeService;
        this.auditService = auditService;
        this.commonService = commonService;
    }

    // ------------------------------------------------------------------
    // Presubmit validation (BEFORE the process instance is created)
    // ------------------------------------------------------------------

    /**
     * Runs the SIS presubmit validation
     * {@code BPM_PKG.check_presubmit_bpm_service} for the given student /
     * semester. Must be executed <b>before</b>
     * {@link #startWithdrawal(String, Map)} creates the Flowable process
     * instance - {@code status == 0} must prevent the submission entirely.
     *
     * <p>Outcome object: {@code status} (1 = ok, 0 = rejected) and
     * {@code msg} (localized rejection reason to display to the
     * student).</p>
     */
    public PresubmitResult checkPresubmit(String username, String semester, String lang) {
        StudentInfoBean sis = commonService.getStudentInfo(username);
        if (sis == null || sis.getStudentId() == null || sis.getStudentId().isBlank()) {
            return new PresubmitResult(0, "Student information not found - cannot submit");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("studentId", sis.getStudentId());
        params.put("semester", semester);
        params.put("documentCode", PRESUBMIT_DOCUMENT_CODE);
        params.put("courseNo", null);
        params.put("courseEdition", null);
        params.put("activityCode", null);
        params.put("section", null);
        params.put("lang", lang != null ? lang : "2");
        try {
            Map<String, Object> out = commonService.checkBpmPresumbitService(params);
            Object status = out != null ? out.get("status") : null;
            Object msg = out != null ? out.get("msg") : null;
            int statusValue = toInt(status, 0);
            String message = msg != null ? msg.toString() : null;
            return new PresubmitResult(statusValue, message);
        } catch (Exception e) {
            log.error("Presubmit validation failed for {} / semester {}", username, semester, e);
            return new PresubmitResult(0, "Presubmit validation failed: " + e.getMessage());
        }
    }

    /** Immutable presubmit outcome: 1 = ok / 0 = rejected + message. */
    public record PresubmitResult(int status, String msg) {

        public boolean isAllowed() {
            return status == PRESUBMIT_STATUS_OK;
        }
    }

    // ------------------------------------------------------------------
    // Start
    // ------------------------------------------------------------------

    /**
     * Start a Semester Withdrawal instance. Only members of the {@code STD}
     * candidate group may do so; this is checked here in addition to the
     * BPMN-level {@code candidateStarterGroups} authorization.
     *
     * <p>The read-only student information variables are (re-)loaded here
     * from the SIS for the initiating student so they always originate from
     * {@link CommonService#getStudentInfo(String)} - never from a client
     * request - and every later task can display them from the process
     * variables without re-querying the SIS with a department employee's
     * username.</p>
     */
    public ProcessInstance startWithdrawal(String username, Map<String, Object> variables) {
        if (!isMemberOfGroup(username, INITIATOR_CANDIDATE_GROUP)) {
            throw new SecurityException("User " + username
                    + " is not allowed to start the Semester Withdrawal process "
                    + "(requires group " + INITIATOR_CANDIDATE_GROUP + ")");
        }
        StudentInfoBean sis = commonService.getStudentInfo(username);
        if (sis == null) {
            throw new IllegalStateException("No student information found for " + username
                    + " - cannot start the Semester Withdrawal process");
        }
        Map<String, Object> vars = new HashMap<>(variables);
        vars.put(VAR_INITIATOR, username);
        vars.put(VAR_STUDENT_ID, sis.getStudentId());
        vars.put(VAR_STUDENT_NAME, sis.getStudentName());
        vars.put(VAR_STUDENT_GPA, sis.getCumStudentGPA());
        vars.put(VAR_CURRENT_SEMESTER, sis.getAcademicYear());
        vars.put(VAR_GENDER, sis.getGender());
        vars.put(VAR_FACULTY_NO, sis.getFacultyNo());
        vars.put(VAR_CAMPUS_NO, sis.getCampusNo());
        return processStartService.startProcess(PROCESS_KEY, username, vars);
    }

    // ------------------------------------------------------------------
    // Task inbox: assignee OR any candidate group of the user
    // ------------------------------------------------------------------

    /**
     * All withdrawal-relevant tasks visible to the user: assigned directly
     * or offered to one of their candidate groups (approval tasks start as
     * group tasks and must be claimed before completion).
     */
    public List<Task> getTasksForUser(String username) {
        List<String> groups = groupIdsOf(username);
        if (groups.isEmpty()) {
            // taskCandidateGroupIn with an empty list is not allowed
            return taskService.createTaskQuery()
                    .taskAssignee(username)
                    .active()
                    .orderByTaskCreateTime().desc()
                    .list();
        }
        return taskService.createTaskQuery()
                .or()
                .taskAssignee(username)
                .taskCandidateGroupIn(groups)
                .endOr()
                .active()
                .orderByTaskCreateTime().desc()
                .list();
    }

    /** Flowable group ids the user belongs to. */
    public List<String> groupIdsOf(String username) {
        return identityService.createGroupQuery()
                .groupMember(username)
                .list()
                .stream()
                .map(Group::getId)
                .toList();
    }

    public boolean isMemberOfGroup(String username, String groupId) {
        return identityService.createGroupQuery()
                .groupMember(username)
                .groupId(groupId)
                .count() > 0;
    }

    // ------------------------------------------------------------------
    // Claim / complete
    // ------------------------------------------------------------------

    /**
     * Claim a candidate-group task (REG / parallel approvers / FIN).
     * Idempotent for already-assigned tasks.
     */
    public void claimTask(String taskId, String username) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new IllegalArgumentException("Task not found: " + taskId);
        }
        if (task.getAssignee() == null) {
            taskService.claim(taskId, username);
            auditClaim(task, username);
        } else if (!username.equals(task.getAssignee())) {
            throw new IllegalStateException("Task " + taskId + " is already claimed by "
                    + task.getAssignee());
        }
    }

    /**
     * CLAIMED audit row (spec: every task lifecycle action audited). The
     * department/candidate group is derived from the task's process
     * variables when available - best effort, never blocks the claim.
     */
    private void auditClaim(Task task, String username) {
        try {
            String pid = task.getProcessInstanceId();
            String department = null;
            if (pid != null && !pid.isBlank()) {
                Object group = runtimeService.getVariable(pid, VAR_APPROVAL_GROUP);
                if (group == null) {
                    Object taskDefKey = task.getTaskDefinitionKey();
                    department = departmentOfTaskKey(taskDefKey != null ? taskDefKey.toString() : null);
                } else {
                    department = WithdrawalTaskListener.partyNameOf(group.toString());
                }
            }
            auditService.logProcessAction(pid,
                    com.example.approval.audit.BpmAuditConstants.ACTION_TASK_CLAIMED,
                    stageOfTaskKey(task.getTaskDefinitionKey()),
                    department, username, null,
                    "Task " + task.getId() + " claimed by " + username);
        } catch (Exception e) {
            log.warn("Claim audit failed for task {} (claim itself succeeded): {}",
                    task.getId(), e.getMessage());
        }
    }

    private String stageOfTaskKey(String taskDefinitionKey) {
        if (taskDefinitionKey == null) {
            return null;
        }
        return switch (taskDefinitionKey) {
            case TASK_REG_APPROVAL -> STAGE_ADMISSION_AND_REGISTRATION;
            case TASK_REG_FYI -> STAGE_REG_FYI;
            case TASK_PARALLEL_APPROVAL -> STAGE_PARALLEL_APPROVAL;
            case TASK_AMENDMENT -> STAGE_AMENDMENT;
            case TASK_FINANCE_APPROVAL -> STAGE_FINANCE;
            case TASK_STUDENT_RESULT -> STAGE_STUDENT_RESULT;
            default -> taskDefinitionKey;
        };
    }

    private String departmentOfTaskKey(String taskDefinitionKey) {
        if (taskDefinitionKey == null) {
            return null;
        }
        return switch (taskDefinitionKey) {
            case TASK_REG_APPROVAL, TASK_REG_FYI -> DEPT_ADMISSION_AND_REGISTRATION;
            case TASK_FINANCE_APPROVAL -> DEPT_FINANCE;
            case TASK_AMENDMENT, TASK_STUDENT_RESULT -> null;
            default -> null;
        };
    }

    /**
     * Complete an approval task (REG / parallel parties / FIN) with an
     * explicit decision. Claims the task first if needed; the shared task
     * listener then writes the audit row and the approval bookkeeping.
     */
    public void completeApprovalTask(String taskId, String decision, String comment, String username) {
        if (!DECISION_APPROVE.equals(decision) && !DECISION_REJECT.equals(decision)) {
            throw new IllegalArgumentException("Decision must be '" + DECISION_APPROVE
                    + "' or '" + DECISION_REJECT + "'");
        }
        claimTask(taskId, username);

        Map<String, Object> vars = new HashMap<>();
        vars.put(VAR_DECISION, decision);
        vars.put(VAR_COMMENT, comment != null ? comment : "");
        vars.put(VAR_COMPLETED_BY, username);
        taskService.complete(taskId, vars);
        log.info("Withdrawal task {} completed by {} with decision {}", taskId, username, decision);
    }

    /**
     * Complete the initiator's "Amend Semester Withdrawal Request" task.
     * The process loops back to dynamic approver resolution automatically
     * (already-approved parties are skipped).
     *
     * <p>The read-only student information is taken from the process
     * variables (unchanged); only the editable fields (reason, semester,
     * note, amendment comment) are updated.</p>
     */
    public void completeAmendment(String taskId,
                                   String withdrawalReason,
                                   String withdrawalReasonDesc,
                                   String withdrawalSemester,
                                   String withdrawalSemesterDesc,
                                   String studentNote,
                                   String amendmentNotes,
                                   String username) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new IllegalArgumentException("Task not found: " + taskId);
        }
        if (!username.equals(task.getAssignee())) {
            throw new IllegalStateException("User " + username + " is not the assignee of " + taskId);
        }

        Map<String, Object> vars = new HashMap<>();
        vars.put(VAR_WITHDRAWAL_REASON, withdrawalReason);
        vars.put(VAR_WITHDRAWAL_REASON_DESC, withdrawalReasonDesc);
        vars.put(VAR_WITHDRAWAL_SEMESTER, withdrawalSemester);
        vars.put(VAR_WITHDRAWAL_SEMESTER_DESC, withdrawalSemesterDesc);
        vars.put(VAR_STUDENT_NOTE, studentNote != null ? studentNote : "");
        vars.put(VAR_AMENDMENT_NOTES, amendmentNotes != null ? amendmentNotes : "");
        taskService.complete(taskId, vars);
        log.info("Amendment task {} completed by {}", taskId, username);
    }

    // ------------------------------------------------------------------
    // Final student result task
    // ------------------------------------------------------------------

    /**
     * Complete the initiator's final "Semester Withdrawal Completed" task:
     * audits the result acknowledgement and ends the process.
     */
    public void acknowledgeResult(String taskId, String username) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new IllegalArgumentException("Task not found: " + taskId);
        }
        if (!username.equals(task.getAssignee())
                && task.getAssignee() != null && !task.getAssignee().isBlank()) {
            throw new IllegalStateException("User " + username + " is not the assignee of " + taskId);
        }
        String pid = task.getProcessInstanceId();
        if (pid != null && !pid.isBlank()) {
            auditService.logProcessAction(pid,
                    com.example.approval.audit.BpmAuditConstants.ACTION_RESULT_ACKNOWLEDGED,
                    STAGE_STUDENT_RESULT, null, username, null,
                    "Task '" + task.getName() + "' acknowledged - semester withdrawal completed");
        }
        taskService.complete(taskId);
        log.info("Result task {} acknowledged by {}", taskId, username);
    }

    // ------------------------------------------------------------------
    // Read helpers for the JSF forms
    // ------------------------------------------------------------------

    public Task getTaskById(String taskId) {
        return taskService.createTaskQuery().taskId(taskId).singleResult();
    }

    public Map<String, Object> getProcessVariables(String processInstanceId) {
        return runtimeService.getVariables(processInstanceId);
    }

    public Map<String, Object> getTaskVariables(String taskId) {
        Task task = getTaskById(taskId);
        if (task == null) {
            return Map.of();
        }
        if (task.getProcessInstanceId() != null && !task.getProcessInstanceId().isEmpty()) {
            return runtimeService.getVariables(task.getProcessInstanceId());
        }
        return taskService.getVariables(taskId);
    }

    private int toInt(Object value, int fallback) {
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
}
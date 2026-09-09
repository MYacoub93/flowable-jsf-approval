package com.example.approval.processes.clearance.service;

import com.example.approval.audit.service.BpmAuditService;
import com.example.approval.origin.beans.StudentInfoBean;
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
import java.util.List;
import java.util.Map;

import static com.example.approval.processes.clearance.ClearanceConstants.*;

/**
 * Facade used by the JSF layer for everything Clearance Letter specific:
 * start authorization (STD), combined assignee/candidate-group task inbox,
 * claim + complete with the decision variables, amendment completion and
 * acknowledgement of the standalone result / FYI tasks.
 *
 * <p>All process-agnostic plumbing (authenticating the initiator user before
 * {@code startProcessInstanceByKey}) stays in {@link ProcessStartService}.</p>
 */
@Service("clearanceService")
@Transactional
public class ClearanceService {

    private static final Logger log = LoggerFactory.getLogger(ClearanceService.class);

    private final ProcessStartService processStartService;
    private final IdentityService identityService;
    private final TaskService taskService;
    private final RuntimeService runtimeService;
    private final BpmAuditService auditService;
    private final CommonService commonService;

    public ClearanceService(ProcessStartService processStartService,
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
    // Start
    // ------------------------------------------------------------------

    /**
     * Start a Clearance Letter instance. Only members of the {@code STD}
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
    public ProcessInstance startClearance(String username, Map<String, Object> variables) {
        if (!isMemberOfGroup(username, INITIATOR_CANDIDATE_GROUP)) {
            throw new SecurityException("User " + username
                    + " is not allowed to start the Clearance Letter process "
                    + "(requires group " + INITIATOR_CANDIDATE_GROUP + ")");
        }
        StudentInfoBean sis = commonService.getStudentInfo(username);
        if (sis == null) {
            throw new IllegalStateException("No student information found for " + username
                    + " - cannot start the Clearance Letter process");
        }
        Map<String, Object> vars = new HashMap<>(variables);
        vars.put(VAR_INITIATOR, username);
        vars.put(VAR_STUDENT_FULL_NAME, sis.getStudentName());
        vars.put(VAR_STUDENT_ID, sis.getStudentId());
        vars.put(VAR_STUDENT_NAME, sis.getStudentName());
        vars.put(VAR_STUDENT_EMAIL, sis.getEmail());
        vars.put(VAR_STUDENT_GPA, sis.getCumStudentGPA());
        vars.put(VAR_STUDENT_MOBILE, sis.getMobile());
        vars.put(VAR_ACADEMIC_YEAR, sis.getAcademicYear());
        return processStartService.startProcess(PROCESS_KEY, username, vars);
    }

    // ------------------------------------------------------------------
    // Task inbox: assignee OR any candidate group of the user
    // ------------------------------------------------------------------

    /**
     * All clearance-relevant tasks visible to the user: assigned directly or
     * offered to one of their candidate groups (department tasks start as
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
     * Claim a candidate-group task (department / Finance / Admission /
     * Internal Audit FYI). Idempotent for already-assigned tasks.
     */
    public void claimTask(String taskId, String username) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new IllegalArgumentException("Task not found: " + taskId);
        }
        if (task.getAssignee() == null) {
            taskService.claim(taskId, username);
        } else if (!username.equals(task.getAssignee())) {
            throw new IllegalStateException("Task " + taskId + " is already claimed by "
                    + task.getAssignee());
        }
    }

    /**
     * Complete an approval task (department / Finance / Admission) with an
     * explicit decision. Claims the task first if needed; the shared task
     * listener then writes the audit row and the department bookkeeping.
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
        log.info("Clearance task {} completed by {} with decision {}", taskId, username, decision);
    }

    /**
     * Complete the initiator's "Amend Clearance Request" task. The process
     * then loops back to dynamic department resolution automatically.
     *
     * <p>The read-only student information is taken from the process
     * variables (unchanged); only the editable fields (program, notes,
     * amendment comment) are updated.</p>
     */
    public void completeAmendment(String taskId,
                                  String studentFullName,
                                  String studentId,
                                  String program,
                                  String notes,
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
        vars.put(VAR_STUDENT_FULL_NAME, studentFullName);
        vars.put(VAR_STUDENT_ID, studentId);
        vars.put(VAR_PROGRAM, program);
        vars.put(VAR_NOTES, notes);
        vars.put(VAR_AMENDMENT_NOTES, amendmentNotes != null ? amendmentNotes : "");
        taskService.complete(taskId, vars);
        log.info("Amendment task {} completed by {}", taskId, username);
    }

    // ------------------------------------------------------------------
    // Standalone result / FYI tasks
    // ------------------------------------------------------------------

    /**
     * Acknowledge a standalone result or FYI task (created by
     * {@code completeClearance}); simply completes it and audits the
     * acknowledgement. These tasks never block the process.
     */
    public void acknowledgeTask(String taskId, String username) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new IllegalArgumentException("Task not found: " + taskId);
        }
        claimTask(taskId, username);

        // standalone tasks carry the process instance id as a task variable
        Object pidValue = taskService.getVariable(taskId, "processInstanceId");
        String pid = pidValue != null ? pidValue.toString() : null;
        boolean isFyi = CATEGORY_FYI.equals(task.getCategory());
        if (pid != null) {
            auditService.logProcessAction(pid,
                    isFyi ? ACTION_FYI_ACKNOWLEDGED : ACTION_RESULT_ACKNOWLEDGED,
                    isFyi ? STAGE_INTERNAL_AUDIT : null,
                    null, username, null,
                    "Task '" + task.getName() + "' acknowledged");
        }
        taskService.complete(taskId);
        log.info("Task {} acknowledged by {}", taskId, username);
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
}
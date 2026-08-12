package com.example.approval.service;

import com.example.approval.flowable.OrganizationManager;
import org.flowable.engine.IdentityService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Core service for starting processes, completing tasks, and resolving dynamic assignees.
 * Referenced from BPMN expressions: ${approvalService.getManager(execution)} etc.
 *
 * Spring injects this bean; Flowable expression language resolves it by name.
 */
@Service("approvalService")
@Transactional
public class ApprovalService {

    private static final Logger log = LoggerFactory.getLogger(ApprovalService.class);

    public static final String PROCESS_KEY = "approvalProcess";

    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final IdentityService identityService;
    private final OrganizationManager userService;

    public ApprovalService(RuntimeService runtimeService,
                           TaskService taskService,
                           IdentityService identityService,
                           OrganizationManager userService) {
        this.runtimeService = runtimeService;
        this.taskService = taskService;
        this.identityService = identityService;
        this.userService = userService;
    }

    // -------------------------------------------------------------------------
    // Dynamic assignee resolution (called from BPMN expressions)
    // -------------------------------------------------------------------------

    /**
     * Called by Flowable when creating the Manager Approval task.
     * Expression: ${approvalService.getManager(execution)}
     */
    public String getManager(org.flowable.engine.delegate.DelegateExecution execution) {
//        String department = (String) execution.getVariable("department");
//        log.info("Resolving manager for department={}", department);
//        User manager = userService.findManagerByDepartment(department);
//        // Store for later use / display
//        execution.setVariable("manager", manager.getUsername());
//        return manager.getUsername();
        return "";
    }

    /**
     * Called by Flowable when creating the Finance Approval task.
     * Expression: ${approvalService.getFinanceApprover(execution)}
     */
    public String getFinanceApprover(org.flowable.engine.delegate.DelegateExecution execution) {
//        Number amount = (Number) execution.getVariable("amount");
//        log.info("Resolving finance approver for amount={}", amount);
//        User finance = userService.findFinanceApprover();
//        execution.setVariable("financeUser", finance.getUsername());
//        return finance.getUsername();
        return "";
    }

    // -------------------------------------------------------------------------
    // Process start
    // -------------------------------------------------------------------------

    /**
     * Start a new approval process instance.
     * Sets authenticated user (initiator) and process variables.
     */
    public ProcessInstance startProcess(String initiatorUsername,
                                        String title,
                                        String description,
                                        Double amount,
                                        String department) {

        org.flowable.idm.api.User initiator = userService.findByUsername(initiatorUsername)
                .orElseThrow();

        // Set authenticated user so Flowable history records the starter
        identityService.setAuthenticatedUserId(initiatorUsername);

        Map<String, Object> variables = new HashMap<>();
        variables.put("initiator", initiatorUsername);
        variables.put("title", title);
        variables.put("description", description);
        variables.put("amount", amount);
        variables.put("department", department);
        variables.put("approved", null);
        variables.put("comments", "");
        variables.put("rejectedBy", null);
        variables.put("manager", null);
        variables.put("financeUser", null);

        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
                PROCESS_KEY,
                "REQ-" + System.currentTimeMillis(), // business key
                variables);

        log.info("Started process instance {} for initiator {}", instance.getId(), initiatorUsername);
        return instance;
    }

    // -------------------------------------------------------------------------
    // Task completion
    // -------------------------------------------------------------------------

    /**
     * Complete an approval task (Manager or Finance).
     *
     * @param taskId   Flowable task id
     * @param approved true = approve, false = reject
     * @param comments free text
     * @param username current user (must match assignee)
     */
    public void completeApprovalTask(String taskId, boolean approved, String comments, String username) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new IllegalArgumentException("Task not found: " + taskId);
        }
        if (!username.equals(task.getAssignee())) {
            throw new IllegalStateException("User " + username + " is not the assignee of task " + taskId);
        }

        Map<String, Object> vars = new HashMap<>();
        vars.put("approved", approved);
        vars.put("comments", comments != null ? comments : "");

        if (!approved) {
            // Determine which task rejected so we know where to return after update
            String taskDefKey = task.getTaskDefinitionKey();
            if ("managerApprovalTask".equals(taskDefKey)) {
                vars.put("rejectedBy", "manager");
            } else if ("financeApprovalTask".equals(taskDefKey)) {
                vars.put("rejectedBy", "finance");
            }
        } else {
            // Clear rejection marker on approve
            vars.put("rejectedBy", null);
        }

        taskService.complete(taskId, vars);
        log.info("Task {} completed by {} with approved={}", taskId, username, approved);
    }

    /**
     * Complete the "Update Request" task after rejection.
     * Updates process variables and continues the flow.
     */
    public void completeUpdateRequest(String taskId,
                                      String title,
                                      String description,
                                      Double amount,
                                      String department,
                                      String username) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new IllegalArgumentException("Task not found: " + taskId);
        }
        if (!username.equals(task.getAssignee())) {
            throw new IllegalStateException("User " + username + " is not the assignee of task " + taskId);
        }

        Map<String, Object> vars = new HashMap<>();
        vars.put("title", title);
        vars.put("description", description);
        vars.put("amount", amount);
        vars.put("department", department);
        // Keep rejectedBy so the gateway knows where to send next
        // Clear previous decision
        vars.put("approved", null);
        vars.put("comments", "");

        taskService.complete(taskId, vars);
        log.info("Update Request task {} completed by {}", taskId, username);
    }

    // -------------------------------------------------------------------------
    // Queries for JSF dashboard / forms
    // -------------------------------------------------------------------------

    public List<Task> getTasksForUser(String username) {
        return taskService.createTaskQuery()
                .taskAssignee(username)
                .orderByTaskCreateTime()
                .desc()
                .list();
    }

    public Task getTaskById(String taskId) {
        return taskService.createTaskQuery().taskId(taskId).singleResult();
    }

    public Map<String, Object> getProcessVariables(String processInstanceId) {
        return runtimeService.getVariables(processInstanceId);
    }

    public List<ProcessInstance> getStartedByUser(String username) {
        return runtimeService.createProcessInstanceQuery()
                .startedBy(username)
                .orderByStartTime()
                .desc()
                .list();
    }

    public ProcessInstance getProcessInstance(String processInstanceId) {
        return runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
    }
}

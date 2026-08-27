package com.example.approval.clearance.flowable;

import com.example.approval.clearance.ClearanceConstants;
import com.example.approval.clearance.model.DepartmentDecision;
import com.example.approval.audit.service.BpmAuditService;
import com.example.approval.clearance.service.DepartmentResolverService;
import com.example.approval.notification.model.NotificationMessage;
import com.example.approval.notification.service.NotificationService;
import org.flowable.engine.TaskService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.task.api.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.example.approval.clearance.ClearanceConstants.*;

/**
 * Single reusable execution-level handler for the Clearance Letter process,
 * referenced from the BPMN via Spring bean expressions such as
 * {@code ${clearanceProcessHandler.resolveRequiredDepartments(execution)}}.
 *
 * <p>It never talks to the database directly - it only orchestrates the three
 * reusable services ({@link DepartmentResolverService},
 * {@link NotificationService}, {@link BpmAuditService}) and manipulates process
 * variables, so the BPMN stays free of duplicated logic.</p>
 */
@Component("clearanceProcessHandler")
public class ClearanceProcessHandler {

    private static final Logger log = LoggerFactory.getLogger(ClearanceProcessHandler.class);

    private final DepartmentResolverService departmentResolverService;
    private final NotificationService notificationService;
    private final BpmAuditService auditService;
    private final TaskService taskService;

    public ClearanceProcessHandler(DepartmentResolverService departmentResolverService,
                                   NotificationService notificationService,
            BpmAuditService auditService,
                                   TaskService taskService) {
        this.departmentResolverService = departmentResolverService;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.taskService = taskService;
    }

    // ------------------------------------------------------------------
    // Start
    // ------------------------------------------------------------------

    /** ExecutionListener (start of process): audit PROCESS_STARTED. */
    public void processStarted(DelegateExecution execution) {
        String initiator = var(execution, VAR_INITIATOR);
//        auditService.logProcessAction(execution.getProcessInstanceId(),
//                ACTION_PROCESS_STARTED, null, null, initiator, initiator,
//                "Clearance request started");
    }

    // ------------------------------------------------------------------
    // Dynamic department resolution (before every approval round)
    // ------------------------------------------------------------------

    /**
     * Service task "Resolve Required Departments". Called before the parallel
     * multi-instance stage is created - initially and again after every
     * amendment - so the department list is always dynamic.
     */
    public void resolveRequiredDepartments(DelegateExecution execution) {
        String initiator = var(execution, VAR_INITIATOR);
        List<String> departments =
                new ArrayList<>(departmentResolverService.getRequiredDepartments(initiator));

        int round = roundOf(execution) + 1;
        execution.setVariable(VAR_REQUIRED_DEPARTMENTS, departments);
        execution.setVariable(VAR_ANY_DEPARTMENT_REJECTED, false);
        execution.setVariable(VAR_DEPARTMENT_DECISIONS, new LinkedHashMap<String, DepartmentDecision>());
        execution.setVariable(VAR_APPROVAL_ROUND, round);

//        auditService.logProcessAction(execution.getProcessInstanceId(),
//                ACTION_DEPARTMENTS_RESOLVED, STAGE_DEPARTMENT_RESOLUTION, null,
//                initiator, initiator,
//                "Round " + round + " requires " + departments.size()
//                        + " departments: " + departments);
        log.info("Clearance {}: round {} departments = {}",
                execution.getProcessInstanceId(), round, departments);
    }

    // ------------------------------------------------------------------
    // Rejection bookkeeping
    // ------------------------------------------------------------------

    /**
     * Service task after the multi-instance stage: copies the first rejection
     * of the round into {@code lastRejected*} variables for the amendment form.
     */
    public void evaluateDepartmentStage(DelegateExecution execution) {
        Map<String, DepartmentDecision> decisions = decisions(execution);
        DepartmentDecision rejection = decisions.values().stream()
                .filter(d -> !d.isApproved())
                .findFirst()
                .orElse(null);
        if (rejection != null) {
            execution.setVariable(VAR_LAST_REJECTED_STAGE, STAGE_DEPARTMENT_APPROVAL);
            execution.setVariable(VAR_LAST_REJECTED_DEPARTMENT, rejection.getDepartment());
            execution.setVariable(VAR_LAST_REJECTION_COMMENT, rejection.getComment());
        }
    }

    /**
     * Service task after Finance / Admission rejection. Called with the stage
     * literal: {@code ${clearanceProcessHandler.recordStageRejection(execution,
     * 'FINANCE', 'Finance Department')}}.
     */
    public void recordStageRejection(DelegateExecution execution, String stage, String department) {
        execution.setVariable(VAR_LAST_REJECTED_STAGE, stage);
        execution.setVariable(VAR_LAST_REJECTED_DEPARTMENT, department);
        execution.setVariable(VAR_LAST_REJECTION_COMMENT, var(execution, VAR_COMMENT));
    }

    /** Service task after the amendment task: audit and reset round state. */
    public void recordAmendment(DelegateExecution execution) {
        String initiator = var(execution, VAR_INITIATOR);
        execution.setVariable(VAR_DECISION, null);
        execution.setVariable(VAR_COMMENT, null);
        auditService.logProcessAction(execution.getProcessInstanceId(),
                ACTION_REQUEST_AMENDED, STAGE_AMENDMENT, null, initiator, initiator,
                "Amendment notes: " + var(execution, VAR_AMENDMENT_NOTES));
    }

    // ------------------------------------------------------------------
    // Completion
    // ------------------------------------------------------------------

    /**
     * Service task "Complete Clearance": runs only after ALL departments,
     * Finance and Admission approved. Sets the final result, notifies the
     * initiator and creates the non-blocking result / FYI tasks.
     */
    public void completeClearance(DelegateExecution execution) {
        String initiator = var(execution, VAR_INITIATOR);
        String pid = execution.getProcessInstanceId();
        execution.setVariable(VAR_CLEARANCE_RESULT, RESULT_APPROVED);

        auditService.logProcessAction(pid, ACTION_PROCESS_COMPLETED, null,
                null, initiator, initiator, "Clearance Letter: Approved");

        // 1) Notify the original initiator by e-mail
        notificationService.send(NotificationMessage.builder()
                .type(NotificationMessage.Type.RESULT)
                .processKey(PROCESS_KEY)
                .processName(PROCESS_NAME)
                .processInstanceId(pid)
                .recipientUser(initiator)
                .initiator(initiator)
                .subject("[" + PROCESS_NAME + "] Clearance Letter: Approved")
                .intro("Clearance Letter: Approved")
                .additionalInfo("The full audit trail is available in the portal.")
                .build());

        // 2) Result task for the initiator (standalone => does not block the process)
        Task resultTask = taskService.newTask();
        resultTask.setName("Clearance Letter: Approved");
        resultTask.setCategory(CATEGORY_RESULT);
        resultTask.setDescription("Your clearance request has been approved by all required "
                + "departments, Finance and Admission & Registration. Process instance: " + pid);
        resultTask.setAssignee(initiator);
        taskService.saveTask(resultTask);
        // standalone tasks have no process instance - store the pid as a task
        // variable so acknowledgement can still audit against the process
        taskService.setVariable(resultTask.getId(), "processInstanceId", pid);
        auditService.logProcessAction(pid, ACTION_TASK_ASSIGNED, STAGE_AMENDMENT,
                null, initiator, initiator,
                "Result task " + resultTask.getId() + " created for initiator (category "
                        + CATEGORY_RESULT + ")");

        // 3) FYI task for Internal Audit (read-only, never blocks the process)
        Task fyiTask = taskService.newTask();
        fyiTask.setName("FYI: Clearance Letter Approved");
        fyiTask.setCategory(CATEGORY_FYI);
        fyiTask.setDescription("Clearance process " + pid + " initiated by " + initiator
                + " has been completed successfully. FYI only - no approval required. "
                + "Process instance: " + pid);
        taskService.saveTask(fyiTask);
        taskService.addCandidateGroup(fyiTask.getId(), GROUP_INTERNAL_AUDIT);
        taskService.setVariable(fyiTask.getId(), "processInstanceId", pid);
        auditService.logProcessAction(pid, ACTION_FYI_CREATED, STAGE_INTERNAL_AUDIT,
                GROUP_INTERNAL_AUDIT, null, initiator,
                "FYI task " + fyiTask.getId() + " created for Internal Audit");

        log.info("Clearance {} completed: result + FYI tasks created", pid);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private String var(DelegateExecution execution, String name) {
        Object value = execution.getVariable(name);
        return value != null ? value.toString() : null;
    }

    private int roundOf(DelegateExecution execution) {
        Object round = execution.getVariable(VAR_APPROVAL_ROUND);
        return round instanceof Number ? ((Number) round).intValue() : 0;
    }

    @SuppressWarnings("unchecked")
    private Map<String, DepartmentDecision> decisions(DelegateExecution execution) {
        Object value = execution.getVariable(VAR_DEPARTMENT_DECISIONS);
        return value instanceof Map ? (Map<String, DepartmentDecision>) value : new LinkedHashMap<>();
    }
}
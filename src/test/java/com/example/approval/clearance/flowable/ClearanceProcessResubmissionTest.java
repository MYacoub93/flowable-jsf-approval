package com.example.approval.clearance.flowable;

import com.example.approval.audit.service.BpmAuditService;
import com.example.approval.processes.clearance.flowable.ClearanceProcessHandler;
import com.example.approval.processes.clearance.flowable.ClearanceTaskListener;
import com.example.approval.processes.clearance.service.DepartmentResolverService;
import com.example.approval.notification.service.NotificationService;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.h2.Driver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.example.approval.processes.clearance.ClearanceConstants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Engine-level regression tests for the Clearance Letter RESUBMISSION rule:
 * when a request is amended after a rejection, only departments that did NOT
 * approve yet may receive a new {@code departmentApprovalTask}.
 *
 * <p><b>Mechanism under test:</b></p>
 * <ul>
 *   <li>{@code ClearanceProcessHandler.evaluateDepartmentStage} adds every
 *       department that approved during a round to the cumulative
 *       {@code approvedDepartments} process variable (survives the whole
 *       amendment loop, unlike {@code departmentDecisions} which is reset
 *       every round);</li>
 *   <li>{@code ClearanceProcessHandler.resolveRequiredDepartments} subtracts
 *       {@code approvedDepartments} from the freshly resolved department set,
 *       so already-approved departments are filtered out of
 *       {@code requiredDepartments} (the multi-instance collection);</li>
 *   <li>the new {@code gatewayDepartmentsPending} gateway skips the
 *       multi-instance stage entirely when nothing is pending (e.g. only
 *       Finance or Admission rejected), so an empty collection never reaches
 *       the multi-instance activity.</li>
 * </ul>
 *
 * <p>Like {@link ClearanceProcessSynchronizationTest}, these tests boot a
 * REAL Flowable engine (H2 in-memory) and deploy the production BPMN
 * {@code processes/clearance-letter-process.bpmn20.xml} unchanged; only the
 * three collaborators are test doubles.</p>
 */
class ClearanceProcessResubmissionTest {

    private static final String INITIATOR = "student.test";

    private static ProcessEngine processEngine;
    private static FixedDepartmentResolver resolver;
    private static NotificationService notificationService;
    private static BpmAuditService auditService;

    @BeforeAll
    static void bootEngineAndDeployProcess() {
        resolver = new FixedDepartmentResolver();
        notificationService = mock(NotificationService.class);
        auditService = mock(BpmAuditService.class);

        GenericApplicationContext applicationContext = new GenericApplicationContext();
        applicationContext.refresh();

        SimpleDriverDataSource dataSource = new SimpleDriverDataSource(new Driver(),
                "jdbc:h2:mem:clearanceResubmitTest;DB_CLOSE_DELAY=-1", "sa", "");

        SpringProcessEngineConfiguration configuration = new SpringProcessEngineConfiguration();
        configuration.setApplicationContext(applicationContext);
        configuration.setDataSource(dataSource);
        configuration.setTransactionManager(new DataSourceTransactionManager(dataSource));
        configuration.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        configuration.setAsyncExecutorActivate(false);
        configuration.setEngineName("clearance-resubmit-test");
        processEngine = configuration.buildProcessEngine();

        // The SpringExpressionManager resolves ${...} beans LAZILY via the
        // bean factory, so the handlers can be registered here - after the
        // engine exists - and still receive the real TaskService.
        applicationContext.getBeanFactory().registerSingleton("clearanceProcessHandler",
                new ClearanceProcessHandler(resolver, notificationService, auditService,
                        processEngine.getTaskService()));
        applicationContext.getBeanFactory().registerSingleton("clearanceTaskListener",
                new ClearanceTaskListener(notificationService, auditService));

        processEngine.getRepositoryService().createDeployment()
                .addClasspathResource("processes/clearance-letter-process.bpmn20.xml")
                .name("clearance-resubmit-test")
                .deploy();
    }

    @AfterAll
    static void shutdownEngine() {
        if (processEngine != null) {
            processEngine.close();
        }
    }

    @BeforeEach
    void resetDepartmentList() {
        resolver.set(List.of());
    }

    // ==================================================================
    // Initial submission - every department receives a task
    // ==================================================================

    @Test
    @DisplayName("Round 1: initial submission sends a task to EVERY department")
    void round1_initialSubmission_allDepartmentsReceiveTasks() {
        resolver.set(List.of(DEPT_DEN, DEPT_HOD, DEPT_IT));
        String pid = startProcess();

        assertEquals(3, activeDepartmentTaskCount(pid));
        assertNotNull(findDepartmentTask(pid, DEPT_DEN));
        assertNotNull(findDepartmentTask(pid, DEPT_HOD));
        assertNotNull(findDepartmentTask(pid, DEPT_IT));
    }

    // ==================================================================
    // Resubmission after partial approval - only rejected departments
    // ==================================================================

    @Test
    @DisplayName("Resubmission after DEN+HOD approved, IT rejected: ONLY IT gets a new task")
    void resubmission_afterPartialApproval_onlyRejectedDepartmentReceivesTask() {
        resolver.set(List.of(DEPT_DEN, DEPT_HOD, DEPT_IT));
        String pid = startProcess();

        // round 1: DEN + HOD approve, IT rejects -> amendment
        completeDepartment(pid, DEPT_DEN, DECISION_APPROVE);
        completeDepartment(pid, DEPT_HOD, DECISION_APPROVE);
        completeDepartment(pid, DEPT_IT, DECISION_REJECT);
        completeAmendment(pid);

        // round 2: ONLY IT (the rejecting department) may circulate again
        assertEquals(1, activeDepartmentTaskCount(pid),
                "only departments that did NOT approve may receive a new task");
        assertNotNull(findDepartmentTask(pid, DEPT_IT),
                "IT rejected round 1 -> IT must receive a new task");
        assertNull(findDepartmentTask(pid, DEPT_DEN),
                "DEN already approved -> no second task");
        assertNull(findDepartmentTask(pid, DEPT_HOD),
                "HOD already approved -> no second task");

        assertEquals(2, approvalRound(pid));
        assertEquals(List.of(DEPT_DEN, DEPT_HOD), approvedDepartments(pid));
    }

    // ==================================================================
    // Resubmission completes - flow continues to Finance
    // ==================================================================

    @Test
    @DisplayName("IT approves on resubmission -> no amendment, Finance reached")
    void resubmission_rejectedDepartmentApproves_flowContinuesToFinance() {
        resolver.set(List.of(DEPT_DEN, DEPT_HOD, DEPT_IT));
        String pid = startProcess();

        completeDepartment(pid, DEPT_DEN, DECISION_APPROVE);
        completeDepartment(pid, DEPT_HOD, DECISION_APPROVE);
        completeDepartment(pid, DEPT_IT, DECISION_REJECT);
        completeAmendment(pid);

        // round 2: the only pending department (IT) approves
        assertEquals(1, activeDepartmentTaskCount(pid));
        completeDepartment(pid, DEPT_IT, DECISION_APPROVE);

        assertEquals(0, activeDepartmentTaskCount(pid));
        assertEquals(0, taskCount(pid, TASK_AMEND), "all approved -> no amendment");
        assertEquals(1, taskCount(pid, TASK_FINANCE_APPROVAL), "Finance must be reached");
    }

    // ==================================================================
    // All departments approved - Finance/Admission rejection must skip
    // the department stage entirely on resubmission
    // ==================================================================

    @Test
    @DisplayName("Finance rejects after all departments approved -> resubmission SKIPS department stage")
    void financeRejection_resubmission_skipsDepartmentStageCompletely() {
        resolver.set(List.of(DEPT_DEN, DEPT_HOD));
        String pid = startProcess();

        completeDepartment(pid, DEPT_DEN, DECISION_APPROVE);
        completeDepartment(pid, DEPT_HOD, DECISION_APPROVE);
        assertEquals(1, taskCount(pid, TASK_FINANCE_APPROVAL));

        completeTask(pid, TASK_FINANCE_APPROVAL, DECISION_REJECT);
        assertEquals(1, taskCount(pid, TASK_AMEND));
        completeAmendment(pid);

        // every department already approved -> NO department task at all,
        // the flow must go straight back to Finance
        assertEquals(0, activeDepartmentTaskCount(pid),
                "no department task may be created - all departments already approved");
        assertEquals(0, taskCount(pid, TASK_AMEND));
        assertEquals(1, taskCount(pid, TASK_FINANCE_APPROVAL),
                "the department stage must be skipped and Finance reached directly");

        // ...and the full happy path still completes afterwards
        completeTask(pid, TASK_FINANCE_APPROVAL, DECISION_APPROVE);
        assertEquals(1, taskCount(pid, TASK_ADMISSION_APPROVAL));
        completeTask(pid, TASK_ADMISSION_APPROVAL, DECISION_APPROVE);
        assertFalse(processAlive(pid), "process must finish after final approval");
    }

    // ==================================================================
    // Admission rejection - same skip behaviour
    // ==================================================================

    @Test
    @DisplayName("Admission rejects -> resubmission goes straight to Admission (departments done)")
    void admissionRejection_resubmission_skipsDepartmentsAndFinance() {
        resolver.set(List.of(DEPT_DEN));
        String pid = startProcess();

        completeDepartment(pid, DEPT_DEN, DECISION_APPROVE);
        completeTask(pid, TASK_FINANCE_APPROVAL, DECISION_APPROVE);
        completeTask(pid, TASK_ADMISSION_APPROVAL, DECISION_REJECT);
        completeAmendment(pid);

        assertEquals(0, activeDepartmentTaskCount(pid),
                "DEN already approved - no new department task");
        assertEquals(1, taskCount(pid, TASK_FINANCE_APPROVAL),
                "department stage skipped, Finance re-evaluated first");
        completeTask(pid, TASK_FINANCE_APPROVAL, DECISION_APPROVE);
        assertEquals(1, taskCount(pid, TASK_ADMISSION_APPROVAL));
    }

    // ==================================================================
    // Multiple resubmissions - the memory keeps growing
    // ==================================================================

    @Test
    @DisplayName("Multiple resubmissions: IT rejects twice, approves on the 3rd round")
    void multipleResubmissions_memoryKeepsGrowing() {
        resolver.set(List.of(DEPT_DEN, DEPT_HOD, DEPT_IT));
        String pid = startProcess();

        // round 1: DEN + HOD approve, IT rejects
        completeDepartment(pid, DEPT_DEN, DECISION_APPROVE);
        completeDepartment(pid, DEPT_HOD, DECISION_APPROVE);
        completeDepartment(pid, DEPT_IT, DECISION_REJECT);
        completeAmendment(pid);

        // round 2: only IT again; rejects again
        assertEquals(1, activeDepartmentTaskCount(pid));
        completeDepartment(pid, DEPT_IT, DECISION_REJECT);
        completeAmendment(pid);

        // round 3: still only IT; approves -> Finance
        assertEquals(1, activeDepartmentTaskCount(pid),
                "after two rejections IT is still the ONLY pending department");
        assertNull(findDepartmentTask(pid, DEPT_DEN));
        assertNull(findDepartmentTask(pid, DEPT_HOD));
        assertEquals(3, approvalRound(pid));
        completeDepartment(pid, DEPT_IT, DECISION_APPROVE);

        // IT finally approved -> now ALL departments are in the cumulative memory
        assertEquals(List.of(DEPT_DEN, DEPT_HOD, DEPT_IT), approvedDepartments(pid));
        assertEquals(1, taskCount(pid, TASK_FINANCE_APPROVAL));
    }

    // ==================================================================
    // helpers
    // ==================================================================

    private String startProcess() {
        Map<String, Object> variables = new HashMap<>();
        variables.put(VAR_INITIATOR, INITIATOR);
        ProcessInstance instance = processEngine.getRuntimeService()
                .startProcessInstanceByKey(PROCESS_KEY, variables);
        return instance.getId();
    }

    /** Completes the still-active department task of {@code department}. */
    private void completeDepartment(String pid, String department, String decision) {
        Task task = requireDepartmentTask(pid, department);
        complete(task.getId(), decision);
    }

    /** Completes the still-active task with the given definition key. */
    private void completeTask(String pid, String taskDefinitionKey, String decision) {
        Task task = processEngine.getTaskService().createTaskQuery()
                .processInstanceId(pid)
                .taskDefinitionKey(taskDefinitionKey)
                .singleResult();
        assertNotNull(task, "expected active task " + taskDefinitionKey);
        complete(task.getId(), decision);
    }

    /** Completes the initiator's amendment task (no decision variable). */
    private void completeAmendment(String pid) {
        Task task = processEngine.getTaskService().createTaskQuery()
                .processInstanceId(pid)
                .taskDefinitionKey(TASK_AMEND)
                .singleResult();
        assertNotNull(task, "expected active amendment task");
        processEngine.getTaskService().complete(task.getId());
    }

    private void complete(String taskId, String decision) {
        Map<String, Object> vars = new HashMap<>();
        vars.put(VAR_DECISION, decision);
        vars.put(VAR_COMMENT, "test comment");
        vars.put(VAR_COMPLETED_BY, "tester");
        processEngine.getTaskService().complete(taskId, vars);
    }

    private Task findDepartmentTask(String pid, String department) {
        return processEngine.getTaskService().createTaskQuery()
                .processInstanceId(pid)
                .taskDefinitionKey(TASK_DEPARTMENT_APPROVAL)
                .taskCandidateGroup(department)
                .singleResult();
    }

    private Task requireDepartmentTask(String pid, String department) {
        Task task = findDepartmentTask(pid, department);
        assertNotNull(task, "expected active approval task for department " + department);
        return task;
    }

    private long activeDepartmentTaskCount(String pid) {
        return taskCount(pid, TASK_DEPARTMENT_APPROVAL);
    }

    private long taskCount(String pid, String taskDefinitionKey) {
        return processEngine.getTaskService().createTaskQuery()
                .processInstanceId(pid)
                .taskDefinitionKey(taskDefinitionKey)
                .count();
    }

    private boolean processAlive(String pid) {
        return processEngine.getRuntimeService().createProcessInstanceQuery()
                .processInstanceId(pid).count() == 1;
    }

    @SuppressWarnings("unchecked")
    private List<String> approvedDepartments(String pid) {
        return (List<String>) processEngine.getRuntimeService()
                .getVariable(pid, VAR_APPROVED_DEPARTMENTS);
    }

    private int approvalRound(String pid) {
        Object round = processEngine.getRuntimeService().getVariable(pid, VAR_APPROVAL_ROUND);
        assertTrue(round instanceof Number, "approvalRound must be set");
        return ((Number) round).intValue();
    }

    /** Deterministic department resolver for tests (no SIS/HR lookup). */
    private static final class FixedDepartmentResolver implements DepartmentResolverService {
        private volatile List<String> departments = List.of();

        void set(List<String> departments) {
            this.departments = departments;
        }

        @Override
        public List<String> getRequiredDepartments(String initiatorUsername) {
            return departments;
        }
    }
}
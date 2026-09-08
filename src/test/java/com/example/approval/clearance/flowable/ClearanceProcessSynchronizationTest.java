package com.example.approval.clearance.flowable;

import com.example.approval.audit.service.BpmAuditService;
import com.example.approval.clearance.ClearanceConstants;
import com.example.approval.clearance.service.DepartmentResolverService;
import com.example.approval.notification.service.NotificationService;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.MultiInstanceLoopCharacteristics;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.engine.repository.ProcessDefinition;
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

import static com.example.approval.clearance.ClearanceConstants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Engine-level regression tests for the Clearance Letter synchronization
 * barrier.
 *
 * <p><b>Background bug:</b> the parallel multi-instance department approval
 * stage used {@code <completionCondition>${anyDepartmentRejected}</completionCondition>}.
 * When that condition becomes true after the FIRST department rejection,
 * Flowable ends the whole multi-instance activity instantly and destroys
 * every still-open sibling task - so the process reached
 * {@code gatewayAllApproved} and created the Amendment Task while other
 * departments still had pending (never submitted) tasks.</p>
 *
 * <p><b>Fix under test:</b> the completion condition is now
 * {@code ${nrOfCompletedInstances == nrOfInstances}} - a pure synchronization
 * barrier. The stage can only be left when EVERY required department
 * submitted its task; a rejection merely sets {@code anyDepartmentRejected},
 * which the <i>gateway</i> evaluates <b>after</b> the barrier released.</p>
 *
 * <p>The tests boot a REAL Flowable engine (H2 in-memory) and deploy the
 * production BPMN {@code processes/clearance-letter-process.bpmn20.xml}
 * unchanged. Only the three collaborators are test doubles:
 * {@link DepartmentResolverService} returns a fixed per-test department list,
 * {@link NotificationService} and {@link BpmAuditService} are no-op mocks.</p>
 */
class ClearanceProcessSynchronizationTest {

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
                "jdbc:h2:mem:clearanceSyncTest;DB_CLOSE_DELAY=-1", "sa", "");

        SpringProcessEngineConfiguration configuration = new SpringProcessEngineConfiguration();
        configuration.setApplicationContext(applicationContext);
        configuration.setDataSource(dataSource);
        configuration.setTransactionManager(new DataSourceTransactionManager(dataSource));
        configuration.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        configuration.setAsyncExecutorActivate(false);
        configuration.setEngineName("clearance-sync-test");
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
                .name("clearance-sync-test")
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
    // Scenario 1 - 2 departments, one still pending
    // ==================================================================

    @Test
    @DisplayName("Scenario 1: 2 departments, B pending -> no Amendment Task, process waits")
    void scenario1_oneDepartmentPending_processWaitsAtBarrier() {
        resolver.set(List.of(DEPT_DEN, DEPT_HOD));
        String pid = startProcess();

        // both parallel instances created immediately (parallel MI)
        assertEquals(2, activeDepartmentTaskCount(pid));

        completeDepartment(pid, DEPT_DEN, DECISION_APPROVE);

        // DEN completed, HOD still pending -> barrier must hold
        assertEquals(1, activeDepartmentTaskCount(pid), "HOD task must still be active");
        assertEquals(0, taskCount(pid, TASK_AMEND),
                "Amendment Task must NOT exist while HOD is still pending");
        assertEquals(0, taskCount(pid, TASK_FINANCE_APPROVAL));
        assertTrue(processAlive(pid), "process must remain waiting at the barrier");
    }

    // ==================================================================
    // Scenario 2 - 2 departments, both completed -> evaluate + continue
    // ==================================================================

    @Test
    @DisplayName("Scenario 2: 2 departments, both completed -> no amendment, Finance reached, process completes")
    void scenario2_allDepartmentsCompleted_flowContinuesWithoutAmendment() {
        resolver.set(List.of(DEPT_DEN, DEPT_HOD));
        String pid = startProcess();

        completeDepartment(pid, DEPT_DEN, DECISION_APPROVE);
        completeDepartment(pid, DEPT_HOD, DECISION_APPROVE);

        // barrier released, all approved -> NO amendment task, Finance reached
        assertEquals(0, activeDepartmentTaskCount(pid));
        assertEquals(0, taskCount(pid, TASK_AMEND));
        assertEquals(1, taskCount(pid, TASK_FINANCE_APPROVAL));

        // continue the sequential stages to the very end
        completeTask(pid, TASK_FINANCE_APPROVAL, DECISION_APPROVE);
        assertEquals(1, taskCount(pid, TASK_ADMISSION_APPROVAL));
        completeTask(pid, TASK_ADMISSION_APPROVAL, DECISION_APPROVE);

        // process finished; result task for the initiator + FYI task created
        assertFalse(processAlive(pid), "process must have ended after final approval");
        assertEquals(1, processEngine.getTaskService().createTaskQuery()
                .taskAssignee(INITIATOR).taskCategory(CATEGORY_RESULT).count());
        assertEquals(1, processEngine.getTaskService().createTaskQuery()
                .taskCandidateGroup(GROUP_INTERNAL_AUDIT).taskCategory(CATEGORY_FYI).count());
    }

    // ==================================================================
    // Scenario 3 - 3 departments, third pending
    // ==================================================================

    @Test
    @DisplayName("Scenario 3: 3 departments, C pending -> no Amendment Task")
    void scenario3_threeDepartments_thirdPending_noAmendmentTask() {
        resolver.set(List.of(DEPT_DEN, DEPT_HOD, DEPT_IT));
        String pid = startProcess();
        assertEquals(3, activeDepartmentTaskCount(pid));

        completeDepartment(pid, DEPT_DEN, DECISION_APPROVE);
        completeDepartment(pid, DEPT_HOD, DECISION_APPROVE);

        assertEquals(1, activeDepartmentTaskCount(pid), "IT task must still be active");
        assertEquals(0, taskCount(pid, TASK_AMEND),
                "Amendment Task must NOT exist while IT is still pending");
        assertEquals(0, taskCount(pid, TASK_FINANCE_APPROVAL));
        assertTrue(processAlive(pid));
    }

    // ==================================================================
    // Scenario 4 - departments complete in a different order
    // ==================================================================

    @Test
    @DisplayName("Scenario 4: completion order C -> A -> B; barrier holds until the LAST task")
    void scenario4_differentCompletionOrder_barrierHoldsUntilLastTask() {
        resolver.set(List.of(DEPT_DEN, DEPT_HOD, DEPT_IT));
        String pid = startProcess();

        completeDepartment(pid, DEPT_IT, DECISION_APPROVE);   // C first
        assertEquals(2, activeDepartmentTaskCount(pid));
        assertEquals(0, taskCount(pid, TASK_FINANCE_APPROVAL));
        assertEquals(0, taskCount(pid, TASK_AMEND));

        completeDepartment(pid, DEPT_DEN, DECISION_APPROVE);  // then A
        assertEquals(1, activeDepartmentTaskCount(pid));
        assertEquals(0, taskCount(pid, TASK_FINANCE_APPROVAL));
        assertEquals(0, taskCount(pid, TASK_AMEND));

        completeDepartment(pid, DEPT_HOD, DECISION_APPROVE);  // B last -> releases the barrier
        assertEquals(0, activeDepartmentTaskCount(pid));
        assertEquals(1, taskCount(pid, TASK_FINANCE_APPROVAL),
                "only after the LAST department the flow may continue");
        assertEquals(0, taskCount(pid, TASK_AMEND),
                "all approved -> no amendment");
    }

    // ==================================================================
    // Scenario 5 - one department rejects while siblings are pending
    // ==================================================================

    @Test
    @DisplayName("Scenario 5: rejection must NOT cancel pending siblings; Amendment Task only after ALL submitted")
    void scenario5_rejectionDoesNotShortCircuit_amendmentOnlyAfterAllDepartmentsSubmitted() {
        resolver.set(List.of(DEPT_DEN, DEPT_HOD, DEPT_IT));
        String pid = startProcess();

        // DEN rejects while HOD and IT are still pending
        completeDepartment(pid, DEPT_DEN, DECISION_REJECT);

        // OLD BUG: the MI stage ended here (completionCondition
        // ${anyDepartmentRejected}), HOD + IT were destroyed and the
        // Amendment Task appeared immediately. The barrier must hold:
        assertEquals(2, activeDepartmentTaskCount(pid),
                "HOD and IT tasks must STILL be active after DEN's rejection");
        assertNotNull(departmentTask(pid, DEPT_HOD));
        assertNotNull(departmentTask(pid, DEPT_IT));
        assertEquals(0, taskCount(pid, TASK_AMEND),
                "Amendment Task must NOT exist while HOD/IT are still pending");
        assertEquals(0, taskCount(pid, TASK_FINANCE_APPROVAL));
        assertTrue(processAlive(pid));
        assertEquals(Boolean.TRUE,
                processEngine.getRuntimeService().getVariable(pid, VAR_ANY_DEPARTMENT_REJECTED));

        // no sibling was cancelled -> no TASK_CANCELLED audit row
        verify(auditService, never()).logProcessAction(anyString(), anyString(),
                eq(ACTION_TASK_CANCELLED), any(), any(), any(), any());

        completeDepartment(pid, DEPT_IT, DECISION_APPROVE);
        assertEquals(1, activeDepartmentTaskCount(pid));
        assertEquals(0, taskCount(pid, TASK_AMEND),
                "still one department pending -> still no Amendment Task");

        // the LAST submission releases the barrier -> NOW the rejection
        // is evaluated and the Amendment Task is created
        completeDepartment(pid, DEPT_HOD, DECISION_APPROVE);
        assertEquals(0, activeDepartmentTaskCount(pid));
        assertEquals(1, taskCount(pid, TASK_AMEND),
                "Amendment Task only now - after ALL departments submitted");

        Task amendment = processEngine.getTaskService().createTaskQuery()
                .processInstanceId(pid).taskDefinitionKey(TASK_AMEND).singleResult();
        assertEquals(INITIATOR, amendment.getAssignee(),
                "amendment task must be assigned to the initiator");
        assertEquals(0, taskCount(pid, TASK_FINANCE_APPROVAL),
                "rejection routes to amendment, not to Finance");

        // ...and the rejection details are available for the amendment form
        assertEquals(STAGE_DEPARTMENT_APPROVAL,
                processEngine.getRuntimeService().getVariable(pid, VAR_LAST_REJECTED_STAGE));
        assertEquals(DEPT_DEN,
                processEngine.getRuntimeService().getVariable(pid, VAR_LAST_REJECTED_DEPARTMENT));
    }

    // ==================================================================
    // Static BPMN guard - the completion condition itself
    // ==================================================================

    @Test
    @DisplayName("BPMN model: parallel MI completion condition == all-instances barrier")
    void bpmnModel_completionConditionIsAllInstancesBarrier() {
        ProcessDefinition definition = processEngine.getRepositoryService()
                .createProcessDefinitionQuery()
                .processDefinitionKey(PROCESS_KEY)
                
                .singleResult();
        assertNotNull(definition, "clearance process must be deployed");

        BpmnModel model = processEngine.getRepositoryService().getBpmnModel(definition.getId());
        FlowElement element = model.getProcessById(PROCESS_KEY).getFlowElement(TASK_DEPARTMENT_APPROVAL);
        UserTask userTask = assertInstanceOf(UserTask.class, element);

        MultiInstanceLoopCharacteristics mi = userTask.getLoopCharacteristics();
        assertNotNull(mi, "department approval must be multi-instance");
        assertFalse(mi.isSequential(), "department approvals must run in PARALLEL");
        assertNotNull(mi.getCompletionCondition(),
                "a completion condition is required so the stage is an explicit barrier");

        String condition = mi.getCompletionCondition().trim();
        assertEquals("${nrOfCompletedInstances == nrOfInstances}", condition,
                "the stage must only complete when EVERY department submitted");
        assertFalse(condition.contains(VAR_ANY_DEPARTMENT_REJECTED),
                "the completion condition must never short-circuit on a single rejection");
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
        Task task = departmentTask(pid, department);
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

    private void complete(String taskId, String decision) {
        Map<String, Object> vars = new HashMap<>();
        vars.put(VAR_DECISION, decision);
        vars.put(VAR_COMMENT, "test comment");
        vars.put(VAR_COMPLETED_BY, "tester");
        processEngine.getTaskService().complete(taskId, vars);
    }

    private Task departmentTask(String pid, String department) {
        Task task = processEngine.getTaskService().createTaskQuery()
                .processInstanceId(pid)
                .taskDefinitionKey(TASK_DEPARTMENT_APPROVAL)
                .taskCandidateGroup(department)
                .singleResult();
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
package com.example.approval.clearance.flowable;

import com.example.approval.audit.service.BpmAuditService;
import com.example.approval.notification.service.NotificationService;
import com.example.approval.processes.clearance.flowable.ClearanceProcessHandler;
import com.example.approval.processes.clearance.flowable.ClearanceTaskListener;
import com.example.approval.processes.clearance.service.DepartmentResolverService;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.service.delegate.DelegateTask;
import org.h2.Driver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.example.approval.processes.clearance.ClearanceConstants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Engine-level regression tests for the <b>post-Admission submission
 * extension point</b> {@code ClearanceTaskListener#handleAdmissionSubmission}:
 *
 * <ul>
 *   <li>completing the Admission & Registration task invokes the hook -</li>
 *   <li>only AFTER the existing decision processing (audit row) ran, and</li>
 *   <li>completing a Finance task, a department task or the amendment task
 *       does NOT invoke it;</li>
 *   <li>a round that reaches Admission again after amendment/resubmission
 *       invokes it again.</li>
 * </ul>
 *
 * <p>The production hook is an empty placeholder, so the observable behavior
 * is tested by subclassing {@code ClearanceTaskListener} and overriding the
 * protected hook to record invocations (no dependence on logging text). A
 * REAL Flowable engine (H2) runs the production BPMN unchanged.</p>
 */
class ClearanceAdmissionSubmissionHookTest {

    private static final String INITIATOR = "student.test";

    /** ordered event log shared by the listener subclass and the audit stub */
    private static final List<String> events = new ArrayList<>();
    private static int hookInvocations;

    /**
     * Production listener with the placeholder hook made observable: every
     * call records "HOOK:<taskId>" into the shared ordered event log.
     */
    private static final class HookRecordingListener extends ClearanceTaskListener {
        HookRecordingListener(NotificationService notificationService,
                BpmAuditService auditService) {
            super(notificationService, auditService);
        }

        @Override
        protected void handleAdmissionSubmission(DelegateTask task) {
            hookInvocations++;
            events.add("HOOK:" + task.getId());
        }
    }

    private static ProcessEngine processEngine;
    private static FixedDepartmentResolver resolver;
    private static NotificationService notificationService;
    private static BpmAuditService auditService;

    @BeforeAll
    static void bootEngineAndDeployProcess() {
        resolver = new FixedDepartmentResolver();
        notificationService = Mockito.mock(NotificationService.class);

        // audit stub that participates in the ordered event log so the test
        // can assert the hook ran AFTER the completion audit row
        auditService = Mockito.mock(BpmAuditService.class);
        Mockito.doAnswer(invocation -> {
            events.add("AUDIT:" + invocation.getArgument(6));
            return null;
        }).when(auditService).logTaskCompleted(Mockito.anyString(), Mockito.anyString(),
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
                Mockito.anyString(), Mockito.any());

        GenericApplicationContext applicationContext = new GenericApplicationContext();
        applicationContext.refresh();

        SimpleDriverDataSource dataSource = new SimpleDriverDataSource(new Driver(),
                "jdbc:h2:mem:clearanceAdmissionHookTest;DB_CLOSE_DELAY=-1", "sa", "");

        org.flowable.spring.SpringProcessEngineConfiguration configuration =
                new org.flowable.spring.SpringProcessEngineConfiguration();
        configuration.setApplicationContext(applicationContext);
        configuration.setDataSource(dataSource);
        configuration.setTransactionManager(new DataSourceTransactionManager(dataSource));
        configuration.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        configuration.setAsyncExecutorActivate(false);
        configuration.setEngineName("clearance-admission-hook-test");
        processEngine = configuration.buildProcessEngine();

        applicationContext.getBeanFactory().registerSingleton("clearanceProcessHandler",
                new ClearanceProcessHandler(resolver, notificationService, auditService,
                        processEngine.getTaskService()));
        applicationContext.getBeanFactory().registerSingleton("clearanceTaskListener",
                new HookRecordingListener(notificationService, auditService));

        processEngine.getRepositoryService().createDeployment()
                .addClasspathResource("processes/clearance-letter-process.bpmn20.xml")
                .name("clearance-admission-hook-test")
                .deploy();
    }

    @AfterAll
    static void shutdownEngine() {
        if (processEngine != null) {
            processEngine.close();
        }
    }

    @BeforeEach
    void resetObservations() {
        resolver.set(List.of());
        events.clear();
        hookInvocations = 0;
    }

    // ------------------------------------------------------------------

    @Test
    @DisplayName("Completing the Admission task invokes the hook AFTER its audit row")
    void completingAdmissionTask_invokesPlaceholderAfterAudit() {
        resolver.set(List.of(DEPT_IT));
        String pid = startProcess();
        completeDepartment(pid, DEPT_IT, DECISION_APPROVE);
        completeTask(pid, TASK_FINANCE_APPROVAL, DECISION_APPROVE);
        assertEquals(0, hookInvocations, "hook must not fire before Admission");

        Task admission = activeTask(pid, TASK_ADMISSION_APPROVAL);
        complete(admission.getId(), DECISION_APPROVE);

        assertEquals(1, hookInvocations,
                "the Admission submission must invoke the placeholder exactly once");
        assertTrue(events.contains("HOOK:" + admission.getId()),
                "hook must be observed for the completed Admission task");

        // ordering: decision audit first, placeholder after
        int auditIndex = events.indexOf("AUDIT:" + admission.getId());
        int hookIndex = events.indexOf("HOOK:" + admission.getId());
        assertTrue(auditIndex >= 0 && hookIndex > auditIndex,
                "hook must run after the completion audit row: " + events);
    }

    @Test
    @DisplayName("Completing a Finance task does NOT invoke the hook")
    void completingFinanceTask_doesNotInvokePlaceholder() {
        resolver.set(List.of(DEPT_IT));
        String pid = startProcess();
        completeDepartment(pid, DEPT_IT, DECISION_APPROVE);
        completeTask(pid, TASK_FINANCE_APPROVAL, DECISION_APPROVE);
        assertEquals(1, taskCount(pid, TASK_ADMISSION_APPROVAL),
                "sanity: the flow reached the Admission stage");

        assertEquals(0, hookInvocations,
                "Finance completion (and the Admission task creation) must not invoke the hook");
        assertFalse(events.stream().anyMatch(e -> e.startsWith("HOOK:")),
                "no HOOK event may be recorded: " + events);
    }

    @Test
    @DisplayName("Completing a department task does NOT invoke the hook")
    void completingDepartmentTask_doesNotInvokePlaceholder() {
        resolver.set(List.of(DEPT_IT));
        String pid = startProcess();
        completeDepartment(pid, DEPT_IT, DECISION_APPROVE);
        assertEquals(1, taskCount(pid, TASK_FINANCE_APPROVAL),
                "sanity: the department stage completed");

        assertEquals(0, hookInvocations,
                "department completion must not invoke the hook");
    }

    @Test
    @DisplayName("Admission completion AFTER amendment/resubmission invokes the hook again")
    void admissionAfterResubmission_invokesPlaceholderAgain() {
        resolver.set(List.of(DEPT_IT));
        String pid = startProcess();

        completeDepartment(pid, DEPT_IT, DECISION_APPROVE);
        completeTask(pid, TASK_FINANCE_APPROVAL, DECISION_REJECT);

        // initiator submits the amendment - must NOT trigger the hook
        completeAmendment(pid);
        assertEquals(0, hookInvocations,
                "amendment completion must not invoke the hook");

        // round 2: department already approved, straight back to Finance
        assertEquals(1, taskCount(pid, TASK_FINANCE_APPROVAL));
        completeTask(pid, TASK_FINANCE_APPROVAL, DECISION_APPROVE);

        Task admission = activeTask(pid, TASK_ADMISSION_APPROVAL);
        complete(admission.getId(), DECISION_APPROVE);

        assertEquals(1, hookInvocations,
                "the re-reached Admission submission must invoke the placeholder");
        assertTrue(events.contains("HOOK:" + admission.getId()));
        int auditIndex = events.indexOf("AUDIT:" + admission.getId());
        int hookIndex = events.indexOf("HOOK:" + admission.getId());
        assertTrue(auditIndex >= 0 && hookIndex > auditIndex,
                "hook must run after the completion audit row: " + events);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private String startProcess() {
        Map<String, Object> variables = new HashMap<>();
        variables.put(VAR_INITIATOR, INITIATOR);
        ProcessInstance instance = processEngine.getRuntimeService()
                .startProcessInstanceByKey(PROCESS_KEY, variables);
        return instance.getId();
    }

    private void completeDepartment(String pid, String department, String decision) {
        Task task = processEngine.getTaskService().createTaskQuery()
                .processInstanceId(pid)
                .taskDefinitionKey(TASK_DEPARTMENT_APPROVAL)
                .taskCandidateGroup(department)
                .singleResult();
        assertNotNull(task, "expected active approval task for department " + department);
        complete(task.getId(), decision);
    }

    private void completeTask(String pid, String taskDefinitionKey, String decision) {
        Task task = activeTask(pid, taskDefinitionKey);
        complete(task.getId(), decision);
    }

    private void completeAmendment(String pid) {
        Task task = activeTask(pid, TASK_AMEND);
        processEngine.getTaskService().complete(task.getId());
    }

    private Task activeTask(String pid, String taskDefinitionKey) {
        Task task = processEngine.getTaskService().createTaskQuery()
                .processInstanceId(pid)
                .taskDefinitionKey(taskDefinitionKey)
                .singleResult();
        assertNotNull(task, "expected active task " + taskDefinitionKey);
        return task;
    }

    private long taskCount(String pid, String taskDefinitionKey) {
        return processEngine.getTaskService().createTaskQuery()
                .processInstanceId(pid)
                .taskDefinitionKey(taskDefinitionKey)
                .count();
    }

    private void complete(String taskId, String decision) {
        Map<String, Object> vars = new HashMap<>();
        vars.put(VAR_DECISION, decision);
        vars.put(VAR_COMMENT, "test comment");
        vars.put(VAR_COMPLETED_BY, "tester");
        processEngine.getTaskService().complete(taskId, vars);
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
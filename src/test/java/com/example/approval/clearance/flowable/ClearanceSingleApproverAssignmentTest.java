package com.example.approval.clearance.flowable;

import com.example.approval.audit.service.BpmAuditService;
import com.example.approval.notification.model.NotificationMessage;
import com.example.approval.notification.service.NotificationService;
import com.example.approval.processes.clearance.flowable.ClearanceProcessHandler;
import com.example.approval.processes.clearance.flowable.ClearanceTaskListener;
import com.example.approval.processes.clearance.service.ClearanceApproverResolverService;
import com.example.approval.processes.clearance.service.DepartmentResolverService;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.task.api.Task;
import org.h2.Driver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.example.approval.processes.clearance.ClearanceConstants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Engine-level tests for the HOD / dean <b>single-approver assignment</b>.
 *
 * <p>When the Clearance Letter process creates the multi-instance
 * {@code departmentApprovalTask} instances, {@link ClearanceTaskListener}
 * asks {@link ClearanceApproverResolverService} for one specific approver
 * for the {@code HOD} and {@code DEN} departments (head of department /
 * dean of the student's faculty, resolved from the SIS). If - and only if -
 * a user id is resolved, it becomes the task assignee; the candidate group
 * stays on the task. Every other department, and every failed resolution,
 * falls back to the normal candidate-group task.</p>
 *
 * <p>Same setup as {@code ClearanceProcessSynchronizationTest}: a REAL
 * Flowable engine (H2) runs the production BPMN unchanged; the department
 * resolver, the approver resolver, the notification and the audit service
 * are test doubles.</p>
 */
class ClearanceSingleApproverAssignmentTest {

    private static final String INITIATOR = "student.test";
    private static final String HOD_USER_ID = "555";
    private static final String DEAN_USER_ID = "777";
    private static final String FACULTY_NO = "12";
    private static final String DEPT_NO = "34";
    private static final String CAMPUS_NO = "1";

    private static ProcessEngine processEngine;
    private static StubDepartmentResolver departmentResolver;
    private static StubApproverResolver approverResolver;
    private static NotificationService notificationService;
    private static BpmAuditService auditService;

    @BeforeAll
    static void bootEngineAndDeployProcess() {
        departmentResolver = new StubDepartmentResolver();
        approverResolver = new StubApproverResolver();
        notificationService = mock(NotificationService.class);
        auditService = mock(BpmAuditService.class);

        GenericApplicationContext applicationContext = new GenericApplicationContext();
        applicationContext.refresh();

        SimpleDriverDataSource dataSource = new SimpleDriverDataSource(new Driver(),
                "jdbc:h2:mem:clearanceApproverTest;DB_CLOSE_DELAY=-1", "sa", "");

        SpringProcessEngineConfiguration configuration = new SpringProcessEngineConfiguration();
        configuration.setApplicationContext(applicationContext);
        configuration.setDataSource(dataSource);
        configuration.setTransactionManager(new DataSourceTransactionManager(dataSource));
        configuration.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        configuration.setAsyncExecutorActivate(false);
        configuration.setEngineName("clearance-approver-test");
        processEngine = configuration.buildProcessEngine();

        applicationContext.getBeanFactory().registerSingleton("clearanceProcessHandler",
                new ClearanceProcessHandler(departmentResolver, notificationService, auditService,
                        processEngine.getTaskService()));
        applicationContext.getBeanFactory().registerSingleton("clearanceTaskListener",
                new ClearanceTaskListener(notificationService, auditService, approverResolver));

        processEngine.getRepositoryService().createDeployment()
                .addClasspathResource("processes/clearance-letter-process.bpmn20.xml")
                .name("clearance-approver-test")
                .deploy();
    }

    @AfterAll
    static void shutdownEngine() {
        if (processEngine != null) {
            processEngine.close();
        }
    }

    @BeforeEach
    void resetStubs() {
        departmentResolver.set(List.of());
        approverResolver.reset();
    }

    // ==================================================================
    // Scenario 1 - HOD / DEN tasks get the single approver as assignee
    // ==================================================================

    @Test
    @DisplayName("HOD and DEN tasks are assigned to the resolved person; other departments stay group tasks")
    void hodAndDeanTasksAreAssignedToSingleApprover() {
        departmentResolver.set(List.of(DEPT_DEN, DEPT_HOD, DEPT_IT));
        String pid = startProcess();

        Task deanTask = departmentTask(pid, DEPT_DEN);
        Task hodTask = departmentTask(pid, DEPT_HOD);
        Task itTask = departmentTask(pid, DEPT_IT);

        assertEquals(DEAN_USER_ID, deanTask.getAssignee(),
                "DEN task must be assigned to the dean");
        assertEquals(HOD_USER_ID, hodTask.getAssignee(),
                "HOD task must be assigned to the head of department");
        assertNull(itTask.getAssignee(),
                "IT is a normal department - its task stays a candidate-group task");

        // the candidate group STAYS on the assigned tasks (group views
        // keep working), and the assignee finds the task in his inbox
        assertTrue(hasCandidateGroup(deanTask, DEPT_DEN));
        assertTrue(hasCandidateGroup(hodTask, DEPT_HOD));
        assertTrue(hasCandidateGroup(itTask, DEPT_IT));
        assertEquals(1, processEngine.getTaskService().createTaskQuery()
                .taskAssignee(HOD_USER_ID).count());
        assertEquals(1, processEngine.getTaskService().createTaskQuery()
                .taskAssignee(DEAN_USER_ID).count());
    }

    @Test
    @DisplayName("The notification for an assigned HOD/DEN task targets exactly that person")
    void notificationTargetsTheSingleApprover() {
        departmentResolver.set(List.of(DEPT_HOD));
        startProcess();

        ArgumentCaptor<NotificationMessage> captor =
                ArgumentCaptor.forClass(NotificationMessage.class);
        verify(notificationService, atLeast(1)).send(captor.capture());
        NotificationMessage hodMessage = captor.getAllValues().stream()
                .filter(m -> DEPT_HOD.equals(m.getDepartment()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no notification for the HOD task"));
        assertEquals(HOD_USER_ID, hodMessage.getAssigneeUser());
    }

    @Test
    @DisplayName("Assigned HOD/DEN tasks complete normally and the process advances to Finance")
    void assignedTasksCompleteNormally() {
        departmentResolver.set(List.of(DEPT_DEN, DEPT_HOD, DEPT_IT));
        String pid = startProcess();

        complete(departmentTask(pid, DEPT_DEN), DECISION_APPROVE);
        complete(departmentTask(pid, DEPT_HOD), DECISION_APPROVE);
        complete(departmentTask(pid, DEPT_IT), DECISION_APPROVE);

        assertEquals(1, taskCount(pid, TASK_FINANCE_APPROVAL),
                "all departments approved - Finance task must exist");
    }

    // ==================================================================
    // Scenario 2 - resolution failure falls back to the group task
    // ==================================================================

    @Test
    @DisplayName("Unresolvable approver: task stays a candidate-group task and the flow works")
    void unresolvableApproverFallsBackToGroupTask() {
        departmentResolver.set(List.of(DEPT_DEN, DEPT_HOD, DEPT_IT));
        approverResolver.unresolvable = true;
        String pid = startProcess();

        assertNull(departmentTask(pid, DEPT_DEN).getAssignee());
        assertNull(departmentTask(pid, DEPT_HOD).getAssignee());
        assertNull(departmentTask(pid, DEPT_IT).getAssignee());
        assertTrue(hasCandidateGroup(departmentTask(pid, DEPT_HOD), DEPT_HOD));

        // the classic group flow still completes the whole stage
        complete(departmentTask(pid, DEPT_DEN), DECISION_APPROVE);
        complete(departmentTask(pid, DEPT_HOD), DECISION_APPROVE);
        complete(departmentTask(pid, DEPT_IT), DECISION_APPROVE);
        assertEquals(1, taskCount(pid, TASK_FINANCE_APPROVAL));
    }

    @Test
    @DisplayName("An exception during resolution never breaks task creation")
    void resolverFailureNeverBreaksTaskCreation() {
        departmentResolver.set(List.of(DEPT_DEN, DEPT_HOD));
        approverResolver.fail = true;
        String pid = startProcess();

        assertNull(departmentTask(pid, DEPT_DEN).getAssignee());
        assertNull(departmentTask(pid, DEPT_HOD).getAssignee());
        assertTrue(hasCandidateGroup(departmentTask(pid, DEPT_DEN), DEPT_DEN));
        assertTrue(hasCandidateGroup(departmentTask(pid, DEPT_HOD), DEPT_HOD));
    }

    // ==================================================================
    // helpers
    // ==================================================================

    private String startProcess() {
        Map<String, Object> variables = new HashMap<>();
        variables.put(VAR_INITIATOR, INITIATOR);
        variables.put(VAR_STUDENT_FACULTY_NO, FACULTY_NO);
        variables.put(VAR_STUDENT_DEPT_NO, DEPT_NO);
        variables.put(VAR_STUDENT_CAMPUS_NO, CAMPUS_NO);
        ProcessInstance instance = processEngine.getRuntimeService()
                .startProcessInstanceByKey(PROCESS_KEY, variables);
        return instance.getId();
    }

    /**
     * The active department approval task of the given department. The
     * multi-instance element variable lives on the execution scope, so a
     * plain (scope-fallback) {@code getVariable} is required. A
     * {@code taskCandidateGroup} query cannot be used: it only matches
     * tasks WITHOUT an assignee, and HOD/DEN tasks have one.
     */
    private Task departmentTask(String pid, String department) {
        return processEngine.getTaskService().createTaskQuery()
                .processInstanceId(pid)
                .taskDefinitionKey(TASK_DEPARTMENT_APPROVAL)
                .active()
                .list().stream()
                .filter(t -> department.equals(
                        processEngine.getTaskService().getVariable(t.getId(), VAR_DEPARTMENT)))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "no active " + department + " department task"));
    }

    private boolean hasCandidateGroup(Task task, String group) {
        return processEngine.getTaskService().getIdentityLinksForTask(task.getId()).stream()
                .anyMatch(link -> "candidate".equals(link.getType())
                        && group.equals(link.getGroupId()));
    }

    private void complete(Task task, String decision) {
        Map<String, Object> vars = new HashMap<>();
        vars.put(VAR_DECISION, decision);
        vars.put(VAR_COMMENT, "test comment");
        vars.put(VAR_COMPLETED_BY,
                task.getAssignee() != null ? task.getAssignee() : "tester");
        processEngine.getTaskService().complete(task.getId(), vars);
    }

    private long taskCount(String pid, String taskDefinitionKey) {
        return processEngine.getTaskService().createTaskQuery()
                .processInstanceId(pid)
                .taskDefinitionKey(taskDefinitionKey)
                .count();
    }

    /** Deterministic department resolver (same pattern as the other engine tests). */
    private static final class StubDepartmentResolver implements DepartmentResolverService {
        private volatile List<String> departments = List.of();

        void set(List<String> departments) {
            this.departments = departments;
        }

        @Override
        public List<String> getRequiredDepartments(String initiatorUsername) {
            return departments;
        }
    }

    /**
     * Deterministic approver resolver: returns fixed user ids for HOD / DEN,
     * nothing (or an exception) on demand. Extends the production class so
     * the listener runs the real wiring.
     */
    private static final class StubApproverResolver extends ClearanceApproverResolverService {
        private volatile boolean unresolvable;
        private volatile boolean fail;

        StubApproverResolver() {
            super(null, null);
        }

        void reset() {
            this.unresolvable = false;
            this.fail = false;
        }

        @Override
        public String resolveSingleApproverId(String department, String facultyNo,
                String deptNo, String campusNo) {
            if (fail) {
                throw new IllegalStateException("simulated SIS outage");
            }
            if (unresolvable) {
                return null;
            }
            if (DEPT_HOD.equals(department)) {
                return HOD_USER_ID;
            }
            if (DEPT_DEN.equals(department)) {
                return DEAN_USER_ID;
            }
            return null;
        }
    }
}

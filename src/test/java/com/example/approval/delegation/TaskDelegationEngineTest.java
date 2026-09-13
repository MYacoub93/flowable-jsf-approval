package com.example.approval.delegation;

import com.example.approval.audit.BpmAuditConstants;
import com.example.approval.audit.service.BpmAuditService;
import com.example.approval.entity.PageResult;
import com.example.approval.service.ExternalGroupService;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.common.engine.impl.identity.Authentication;
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
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * End-to-end tests for the Task Delegation feature against a REAL Flowable
 * engine (H2, same boot pattern as
 * {@code ClearanceSingleApproverAssignmentTest}): a two-user-task process
 * with candidate groups is deployed, instances are started and delegated
 * through the production {@link TaskDelegationService}. The external group
 * service (admin role) and the BPM audit service are test doubles; every
 * engine interaction goes through the real engine.
 *
 * <p>Covers the acceptance flow
 * {@code task assigned to userA -> admin selects userB -> Assign -> task
 * assigned to userB}: the previous assignee is replaced, the candidate
 * groups stay untouched, the audit trail is written, the refreshed active
 * task table shows the new assignee immediately and completed tasks are
 * never delegatable.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TaskDelegationEngineTest {

    private static final String ADMIN = "1";
    private static final String ATTACKER = "666";
    private static final String STARTER = "student.test";
    private static final String USER_A = "userA";
    private static final String USER_B = "userB";
    private static final String PROCESS_KEY = "delegationTestProcess";
    private static final String BPMN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:flowable="http://flowable.org/bpmn"
                         targetNamespace="http://flowable.org/test">
              <process id="delegationTestProcess" name="Delegation Test Process" isExecutable="true">
                <startEvent id="start"/>
                <sequenceFlow id="f1" sourceRef="start" targetRef="approvalTask"/>
                <userTask id="approvalTask" name="Department Approval"
                          flowable:candidateGroups="deptA"/>
                <sequenceFlow id="f2" sourceRef="approvalTask" targetRef="reviewTask"/>
                <userTask id="reviewTask" name="Final Review"
                          flowable:candidateGroups="deptB"/>
                <sequenceFlow id="f3" sourceRef="reviewTask" targetRef="end"/>
                <endEvent id="end"/>
              </process>
            </definitions>
            """;

    private static ProcessEngine processEngine;
    private static ExternalGroupService groupService;
    private static BpmAuditService auditService;
    private TaskDelegationService service;

    @BeforeAll
    static void bootEngineAndDeployProcess() {
        groupService = mock(ExternalGroupService.class);
        auditService = mock(BpmAuditService.class);

        GenericApplicationContext applicationContext = new GenericApplicationContext();
        applicationContext.refresh();

        SimpleDriverDataSource dataSource = new SimpleDriverDataSource(new Driver(),
                "jdbc:h2:mem:taskDelegationTest;DB_CLOSE_DELAY=-1", "sa", "");

        SpringProcessEngineConfiguration configuration = new SpringProcessEngineConfiguration();
        configuration.setApplicationContext(applicationContext);
        configuration.setDataSource(dataSource);
        configuration.setTransactionManager(new DataSourceTransactionManager(dataSource));
        configuration.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        configuration.setAsyncExecutorActivate(false);
        configuration.setEngineName("task-delegation-test");
        processEngine = configuration.buildProcessEngine();

        processEngine.getRepositoryService().createDeployment()
                .addInputStream("delegation-test.bpmn20.xml",
                        new ByteArrayInputStream(BPMN.getBytes(StandardCharsets.UTF_8)))
                .name("task-delegation-test")
                .deploy();
    }

    @AfterAll
    static void shutdownEngine() {
        if (processEngine != null) {
            processEngine.close();
        }
    }

    @BeforeEach
    void setUp() {
        Mockito.reset(groupService, auditService);
        when(groupService.isGroupAdmin(ADMIN)).thenReturn(true);
        when(groupService.isGroupAdmin(ATTACKER)).thenReturn(false);
        service = new TaskDelegationService(processEngine.getRepositoryService(),
                processEngine.getRuntimeService(), processEngine.getTaskService(),
                groupService, auditService);
    }

    // ==================================================================
    // Process dropdown + process instances
    // ==================================================================

    @Test
    @DisplayName("Dropdown lists the deployed process; instances load only for the selected process")
    void processSelectionLoadsItsInstances() {
        String pidA = startInstance();
        String pidB = startInstance();
        try {
            assertThat(service.findDeployedProcesses(ADMIN))
                    .extracting(ProcessDefinitionOption::getKey)
                    .contains(PROCESS_KEY);

            PageResult<ProcessInstanceRow> page =
                    service.findProcessInstances(ADMIN, PROCESS_KEY, 1, 10, null, null);
            assertThat(page.getTotalRows()).isEqualTo(2L);
            assertThat(page.getRows())
                    .extracting(ProcessInstanceRow::getStartedBy)
                    .containsOnly(STARTER);
        } finally {
            deleteInstance(pidA);
            deleteInstance(pidB);
        }
    }

    @Test
    @DisplayName("Instances of other processes and completed instances are not listed")
    void instanceListing_excludesOtherProcessesAndCompletedInstances() {
        String pid = startInstance();
        try {
            // unrelated key -> nothing
            assertThat(service.findProcessInstances(ADMIN, "noSuchProcessKey",
                    1, 10, null, null).getTotalRows()).isZero();

            // complete the whole instance -> no longer running/listed
            completeActiveTask(pid);
            completeActiveTask(pid);
            assertThat(service.findProcessInstances(ADMIN, PROCESS_KEY,
                    1, 10, pid, null).getTotalRows()).isZero();
        } finally {
            deleteInstance(pid);
        }
    }

    @Test
    @DisplayName("Instance filter by id and creation date narrow the result")
    void instanceFilters_work() {
        String pid = startInstance();
        try {
            assertThat(service.findProcessInstances(ADMIN, PROCESS_KEY,
                    1, 10, pid, null).getTotalRows()).isEqualTo(1L);
            assertThat(service.findProcessInstances(ADMIN, PROCESS_KEY,
                    1, 10, "no-such-id", null).getTotalRows()).isZero();

            // created today -> found; created tomorrow -> nothing
            assertThat(service.findProcessInstances(ADMIN, PROCESS_KEY,
                    1, 10, null, today()).getTotalRows()).isEqualTo(1L);
            assertThat(service.findProcessInstances(ADMIN, PROCESS_KEY,
                    1, 10, null, tomorrow()).getTotalRows()).isZero();
        } finally {
            deleteInstance(pid);
        }
    }

    // ==================================================================
    // Active tasks
    // ==================================================================

    @Test
    @DisplayName("Active tasks: only active tasks of the selected instance, candidate groups joined, filters work")
    void activeTasks_onlyActiveOfSelectedInstance() {
        String pid = startInstance();
        try {
            PageResult<TaskRow> page =
                    service.findActiveTasks(ADMIN, pid, 1, 10, null, null);
            assertThat(page.getTotalRows()).isEqualTo(1L);
            TaskRow row = page.getRows().get(0);
            assertThat(row.getName()).isEqualTo("Department Approval");
            assertThat(row.getCandidateGroups()).isEqualTo("deptA");

            // task id filter
            assertThat(service.findActiveTasks(ADMIN, pid, 1, 10,
                    row.getId(), null).getTotalRows()).isEqualTo(1L);
            assertThat(service.findActiveTasks(ADMIN, pid, 1, 10,
                    "no-such-task", null).getTotalRows()).isZero();

            // creation date filter
            assertThat(service.findActiveTasks(ADMIN, pid, 1, 10,
                    null, today()).getTotalRows()).isEqualTo(1L);
            assertThat(service.findActiveTasks(ADMIN, pid, 1, 10,
                    null, tomorrow()).getTotalRows()).isZero();

            // after completing the first task only the second stays active -
            // the completed one must never appear
            completeActiveTask(pid);
            PageResult<TaskRow> after =
                    service.findActiveTasks(ADMIN, pid, 1, 10, null, null);
            assertThat(after.getTotalRows()).isEqualTo(1L);
            assertThat(after.getRows().get(0).getName()).isEqualTo("Final Review");
            assertThat(after.getRows().get(0).getCandidateGroups()).isEqualTo("deptB");
        } finally {
            deleteInstance(pid);
        }
    }

    // ==================================================================
    // Delegation end-to-end (the acceptance scenario)
    // ==================================================================

    @Test
    @DisplayName("userA -> admin delegates to userB: assignee replaced, candidate groups unchanged, audited, table refreshed")
    void delegationEndToEnd() {
        String pid = startInstance();
        try {
            Task task = activeTask(pid);
            processEngine.getTaskService().setAssignee(task.getId(), USER_A);

            TaskDelegationService.DelegationResult result =
                    service.delegateTask(ADMIN, task.getId(), USER_B);

            // previous assignee replaced, new assignee set
            assertThat(result.previousAssignee()).isEqualTo(USER_A);
            assertThat(result.newAssignee()).isEqualTo(USER_B);
            assertThat(result.processInstanceId()).isEqualTo(pid);
            Task reloaded = processEngine.getTaskService().createTaskQuery()
                    .taskId(task.getId()).singleResult();
            assertThat(reloaded.getAssignee()).isEqualTo(USER_B);
            assertThat(processEngine.getTaskService().createTaskQuery()
                    .taskAssignee(USER_A).count()).isZero();
            assertThat(processEngine.getTaskService().createTaskQuery()
                    .taskAssignee(USER_B).count()).isEqualTo(1L);

            // candidate group is untouched - delegation only, no group change
            assertThat(processEngine.getTaskService().getIdentityLinksForTask(reloaded.getId()).stream()
                    .anyMatch(l -> "candidate".equals(l.getType())
                            && "deptA".equals(l.getGroupId()))).isTrue();

            // audit trail through the existing BPM audit service
            verify(auditService).logProcessAction(eq(pid),
                    eq(BpmAuditConstants.ACTION_TASK_DELEGATED),
                    eq("approvalTask"), isNull(), eq(ADMIN),
                    eq(USER_A), contains(USER_B));

            // refreshed active task table shows the new assignee immediately
            PageResult<TaskRow> refreshed =
                    service.findActiveTasks(ADMIN, pid, 1, 10, null, null);
            assertThat(refreshed.getRows().get(0).getAssignee()).isEqualTo(USER_B);
        // username lookup yields nothing here (mocked service) - the display
        // falls back to the bare assignee id
        assertThat(refreshed.getRows().get(0).getAssigneeDisplay()).isEqualTo(USER_B);
            assertThat(refreshed.getRows().get(0).getCandidateGroups()).isEqualTo("deptA");
        } finally {
            deleteInstance(pid);
        }
    }

    @Test
    @DisplayName("Delegation of an unassigned task records (none) as previous assignee")
    void delegationOfUnassignedTask() {
        String pid = startInstance();
        try {
            Task task = activeTask(pid);
            TaskDelegationService.DelegationResult result =
                    service.delegateTask(ADMIN, task.getId(), USER_B);
            assertThat(result.previousAssignee()).isNull();
            assertThat(processEngine.getTaskService().createTaskQuery()
                    .taskId(task.getId()).singleResult().getAssignee()).isEqualTo(USER_B);
        } finally {
            deleteInstance(pid);
        }
    }

    @Test
    @DisplayName("A completed task can no longer be delegated")
    void completedTaskCannotBeDelegated() {
        String pid = startInstance();
        try {
            Task task = activeTask(pid);
            processEngine.getTaskService().complete(task.getId());

            assertThatThrownBy(() -> service.delegateTask(ADMIN, task.getId(), USER_B))
                    .isInstanceOf(FlowableObjectNotFoundException.class);
            verifyNoInteractions(auditService);
        } finally {
            deleteInstance(pid);
        }
    }

    @Test
    @DisplayName("A non-admin is rejected server-side and never mutates the engine")
    void nonAdminCannotDelegate() {
        String pid = startInstance();
        try {
            Task task = activeTask(pid);
            processEngine.getTaskService().setAssignee(task.getId(), USER_A);

            assertThatThrownBy(() -> service.delegateTask(ATTACKER, task.getId(), USER_B))
                    .isInstanceOf(SecurityException.class);

            Task unchanged = processEngine.getTaskService().createTaskQuery()
                    .taskId(task.getId()).singleResult();
            assertThat(unchanged.getAssignee()).isEqualTo(USER_A);
            verifyNoInteractions(auditService);
        } finally {
            deleteInstance(pid);
        }
    }

    // ==================================================================
    // helpers
    // ==================================================================

    private String startInstance() {
        try {
            Authentication.setAuthenticatedUserId(STARTER);
            ProcessInstance instance = processEngine.getRuntimeService()
                    .startProcessInstanceByKey(PROCESS_KEY);
            return instance.getId();
        } finally {
            Authentication.setAuthenticatedUserId(null);
        }
    }

    private Task activeTask(String pid) {
        return processEngine.getTaskService().createTaskQuery()
                .processInstanceId(pid)
                .active()
                .singleResult();
    }

    private void completeActiveTask(String pid) {
        Task task = activeTask(pid);
        processEngine.getTaskService().complete(task.getId());
    }

    private void deleteInstance(String pid) {
        // a completed instance is already gone from the runtime tables
        if (processEngine.getRuntimeService().createProcessInstanceQuery()
                .processInstanceId(pid).count() > 0) {
            processEngine.getRuntimeService().deleteProcessInstance(pid, "test cleanup");
        }
    }

    private static Date dayFromToday(int dayOffset) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_MONTH, dayOffset);
        return calendar.getTime();
    }

    private static Date today() {
        return dayFromToday(0);
    }

    private static Date tomorrow() {
        return dayFromToday(1);
    }
}
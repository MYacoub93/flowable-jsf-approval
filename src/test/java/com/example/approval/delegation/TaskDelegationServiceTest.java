package com.example.approval.delegation;

import com.example.approval.audit.BpmAuditConstants;
import com.example.approval.audit.service.BpmAuditService;
import com.example.approval.entity.ExternalUser;
import com.example.approval.entity.PageResult;
import com.example.approval.service.ExternalGroupService;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.identitylink.api.IdentityLinkType;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TaskDelegationService} (Flowable services mocked).
 * Focus: the admin-only guards, the dropdown, server-side pagination and
 * the three filters of the process instances / active tasks listings, and
 * the delegation itself (setAssignee, audit, validation, failure).
 */
@ExtendWith(MockitoExtension.class)
class TaskDelegationServiceTest {

    private static final String ADMIN = "admin";
    private static final String ATTACKER = "attacker";

    @Mock
    private RepositoryService repositoryService;

    @Mock
    private RuntimeService runtimeService;

    @Mock
    private TaskService taskService;

    @Mock
    private ExternalGroupService groupService;

    @Mock
    private BpmAuditService auditService;

    @Mock
    private ProcessDefinitionQuery definitionQuery;

    @Mock
    private ProcessInstanceQuery instanceQuery;

    @Mock
    private TaskQuery taskQuery;

    private TaskDelegationService service;

    @BeforeEach
    void setUp() {
        service = new TaskDelegationService(repositoryService, runtimeService,
                taskService, groupService, auditService);
    }

    private void asAdmin() {
        when(groupService.isGroupAdmin(ADMIN)).thenReturn(true);
    }

    private void asNonAdmin() {
        when(groupService.isGroupAdmin(ATTACKER)).thenReturn(false);
    }

    // ------------------------------------------------------------------
    // Security (server side, independent from the hidden menu)
    // ------------------------------------------------------------------

    @Test
    void allOperations_rejectNonAdmin() {
        asNonAdmin();
        assertThatThrownBy(() -> service.findDeployedProcesses(ATTACKER))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.findProcessInstances(ATTACKER, "k", 1, 10, null, null))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.findActiveTasks(ATTACKER, "pi-1", 1, 10, null, null))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.delegateTask(ATTACKER, "t-1", "userB"))
                .isInstanceOf(SecurityException.class);
        verifyNoInteractions(repositoryService, runtimeService, taskService);
        verify(taskService, never()).setAssignee(anyString(), anyString());
    }

    @Test
    void isDelegationAllowed_delegatesToGroupAdminCheck() {
        when(groupService.isGroupAdmin("admin")).thenReturn(true);
        when(groupService.isGroupAdmin("other")).thenReturn(false);
        assertThat(service.isDelegationAllowed("admin")).isTrue();
        assertThat(service.isDelegationAllowed("other")).isFalse();
    }

    // ------------------------------------------------------------------
    // Process dropdown
    // ------------------------------------------------------------------

    @Test
    void findDeployedProcesses_mapsDefinitionsAndBuildsLabels() {
        asAdmin();
        when(repositoryService.createProcessDefinitionQuery()).thenReturn(definitionQuery);
        when(definitionQuery.latestVersion()).thenReturn(definitionQuery);
        when(definitionQuery.orderByProcessDefinitionKey()).thenReturn(definitionQuery);
        when(definitionQuery.asc()).thenReturn(definitionQuery);
        ProcessDefinition named = mock(ProcessDefinition.class);
        when(named.getId()).thenReturn("def-1");
        when(named.getKey()).thenReturn("clearanceLetter");
        when(named.getName()).thenReturn("Clearance Letter");
        ProcessDefinition unnamed = mock(ProcessDefinition.class);
        when(unnamed.getId()).thenReturn("def-2");
        when(unnamed.getKey()).thenReturn("studentProof");
        when(unnamed.getName()).thenReturn(null);
        when(definitionQuery.list()).thenReturn(List.of(named, unnamed));

        List<ProcessDefinitionOption> options =
                service.findDeployedProcesses(ADMIN);

        assertThat(options).hasSize(2);
        assertThat(options.get(0).getId()).isEqualTo("def-1");
        assertThat(options.get(0).getKey()).isEqualTo("clearanceLetter");
        assertThat(options.get(0).getLabel()).isEqualTo("Clearance Letter (clearanceLetter)");
        assertThat(options.get(1).getLabel()).isEqualTo("studentProof");
    }

    // ------------------------------------------------------------------
    // Process instances: pagination + filters
    // ------------------------------------------------------------------

    @Test
    void findProcessInstances_blankKey_returnsEmptyWithoutQuerying() {
        asAdmin();
        PageResult<ProcessInstanceRow> result =
                service.findProcessInstances(ADMIN, "  ", 1, 10, null, null);
        assertThat(result.getRows()).isEmpty();
        assertThat(result.getTotalRows()).isZero();
        verifyNoInteractions(runtimeService);
    }

    @Test
    void findProcessInstances_secondPage_usesServerSideOffset() {
        asAdmin();
        when(runtimeService.createProcessInstanceQuery()).thenReturn(instanceQuery);
        when(instanceQuery.processDefinitionKey("k")).thenReturn(instanceQuery);
        when(instanceQuery.count()).thenReturn(25L);
        when(instanceQuery.orderByStartTime()).thenReturn(instanceQuery);
        when(instanceQuery.desc()).thenReturn(instanceQuery);
        ProcessInstance instance = mock(ProcessInstance.class);
        when(instance.getId()).thenReturn("pi-11");
        when(instance.getName()).thenReturn("Clearance of Ali");
        when(instance.getStartUserId()).thenReturn("student.test");
        when(instance.getStartTime()).thenReturn(new Date());
        when(instanceQuery.listPage(10, 10)).thenReturn(List.of(instance));

        PageResult<ProcessInstanceRow> page =
                service.findProcessInstances(ADMIN, "k", 2, 10, null, null);

        assertThat(page.getTotalRows()).isEqualTo(25L);
        assertThat(page.getPageNumber()).isEqualTo(2);
        assertThat(page.getRows()).hasSize(1);
        ProcessInstanceRow row = page.getRows().get(0);
        assertThat(row.getId()).isEqualTo("pi-11");
        assertThat(row.getName()).isEqualTo("Clearance of Ali");
        assertThat(row.getStartedBy()).isEqualTo("student.test");
        verify(instanceQuery).listPage(10, 10);
    }

    @Test
    void findProcessInstances_pageBeyondEnd_clampsToLastPage() {
        asAdmin();
        when(runtimeService.createProcessInstanceQuery()).thenReturn(instanceQuery);
        when(instanceQuery.processDefinitionKey("k")).thenReturn(instanceQuery);
        when(instanceQuery.count()).thenReturn(25L);
        when(instanceQuery.orderByStartTime()).thenReturn(instanceQuery);
        when(instanceQuery.desc()).thenReturn(instanceQuery);
        when(instanceQuery.listPage(20, 10)).thenReturn(List.of(mock(ProcessInstance.class)));

        PageResult<ProcessInstanceRow> page =
                service.findProcessInstances(ADMIN, "k", 9, 10, null, null);

        assertThat(page.getPageNumber()).isEqualTo(3);
        verify(instanceQuery).listPage(20, 10);
    }

    @Test
    void findProcessInstances_filterByInstanceId() {
        asAdmin();
        when(runtimeService.createProcessInstanceQuery()).thenReturn(instanceQuery);
        when(instanceQuery.processDefinitionKey("k")).thenReturn(instanceQuery);
        when(instanceQuery.processInstanceId("pi-42")).thenReturn(instanceQuery);
        when(instanceQuery.count()).thenReturn(1L);
        when(instanceQuery.orderByStartTime()).thenReturn(instanceQuery);
        when(instanceQuery.desc()).thenReturn(instanceQuery);
        when(instanceQuery.listPage(0, 10)).thenReturn(List.of(mock(ProcessInstance.class)));

        service.findProcessInstances(ADMIN, "k", 1, 10, " pi-42 ", null);

        verify(instanceQuery).processInstanceId("pi-42");
    }

    @Test
    void findProcessInstances_filterByCreationDate_usesWholeDayBounds() {
        asAdmin();
        when(runtimeService.createProcessInstanceQuery()).thenReturn(instanceQuery);
        when(instanceQuery.processDefinitionKey("k")).thenReturn(instanceQuery);
        when(instanceQuery.count()).thenReturn(0L);
        when(instanceQuery.startedAfter(any(Date.class))).thenReturn(instanceQuery);
        when(instanceQuery.startedBefore(any(Date.class))).thenReturn(instanceQuery);

        Date noon = newCalendarDate(2026, Calendar.MARCH, 15, 12, 30);
        service.findProcessInstances(ADMIN, "k", 1, 10, null, noon);

        org.mockito.ArgumentCaptor<Date> after =
                org.mockito.ArgumentCaptor.forClass(Date.class);
        org.mockito.ArgumentCaptor<Date> before =
                org.mockito.ArgumentCaptor.forClass(Date.class);
        verify(instanceQuery).startedAfter(after.capture());
        verify(instanceQuery).startedBefore(before.capture());
        Calendar start = Calendar.getInstance();
        start.setTime(after.getValue());
        assertThat(start.get(Calendar.HOUR_OF_DAY)).isZero();
        assertThat(start.get(Calendar.MINUTE)).isZero();
        assertThat(start.get(Calendar.SECOND)).isZero();
        assertThat(start.get(Calendar.MILLISECOND)).isZero();
        Calendar end = Calendar.getInstance();
        end.setTime(before.getValue());
        assertThat(end.get(Calendar.HOUR_OF_DAY)).isEqualTo(23);
        assertThat(end.get(Calendar.MINUTE)).isEqualTo(59);
        assertThat(end.get(Calendar.SECOND)).isEqualTo(59);
        assertThat(end.get(Calendar.MILLISECOND)).isEqualTo(999);
        // both bounds stay on the selected day
        assertThat(start.get(Calendar.DAY_OF_MONTH)).isEqualTo(15);
        assertThat(end.get(Calendar.DAY_OF_MONTH)).isEqualTo(15);
    }

    // ------------------------------------------------------------------
    // Active tasks: pagination + filters
    // ------------------------------------------------------------------

    @Test
    void findActiveTasks_blankInstanceId_returnsEmptyWithoutQuerying() {
        asAdmin();
        PageResult<TaskRow> result =
                service.findActiveTasks(ADMIN, "", 1, 10, null, null);
        assertThat(result.getRows()).isEmpty();
        verifyNoInteractions(taskService);
    }

    @Test
    void findActiveTasks_onlyActiveOfInstance_candidateGroupsJoined() {
        asAdmin();
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.processInstanceId("pi-1")).thenReturn(taskQuery);
        when(taskQuery.active()).thenReturn(taskQuery);
        when(taskQuery.count()).thenReturn(1L);
        when(taskQuery.orderByTaskCreateTime()).thenReturn(taskQuery);
        when(taskQuery.desc()).thenReturn(taskQuery);
        Task task = mock(Task.class);
        when(task.getId()).thenReturn("t-1");
        when(task.getName()).thenReturn("Department Approval");
        when(task.getAssignee()).thenReturn("userA");
        when(task.getCreateTime()).thenReturn(new Date());
        when(task.getDueDate()).thenReturn(new Date());
        when(task.getTaskDefinitionKey()).thenReturn("departmentApprovalTask");
        when(taskQuery.listPage(0, 10)).thenReturn(List.of(task));

        IdentityLink candidateA = link(IdentityLinkType.CANDIDATE, "grpB");
        IdentityLink candidateB = link(IdentityLinkType.CANDIDATE, "grpA");
        IdentityLink candidateDuplicate = link(IdentityLinkType.CANDIDATE, "grpA");
        IdentityLink candidateNoGroup = link(IdentityLinkType.CANDIDATE, null);
        IdentityLink assigneeLink = link(IdentityLinkType.ASSIGNEE, "userA");
        when(taskService.getIdentityLinksForTask("t-1"))
                .thenReturn(List.of(candidateA, candidateB, candidateDuplicate,
                        candidateNoGroup, assigneeLink));

        PageResult<TaskRow> page =
                service.findActiveTasks(ADMIN, "pi-1", 1, 10, null, null);

        assertThat(page.getTotalRows()).isEqualTo(1L);
        TaskRow row = page.getRows().get(0);
        assertThat(row.getId()).isEqualTo("t-1");
        assertThat(row.getName()).isEqualTo("Department Approval");
        assertThat(row.getAssignee()).isEqualTo("userA");
        assertThat(row.getCandidateGroups()).isEqualTo("grpA, grpB");
        // only active tasks are ever requested from the engine
        verify(taskQuery).active();
        verify(taskQuery).processInstanceId("pi-1");
    }

    @Test
    void findActiveTasks_resolvesAssigneeUsernameFromUsersView() {
        when(groupService.isGroupAdmin(ADMIN)).thenReturn(true);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.processInstanceId("pi-1")).thenReturn(taskQuery);
        when(taskQuery.active()).thenReturn(taskQuery);
        when(taskQuery.count()).thenReturn(1L);
        when(taskQuery.orderByTaskCreateTime()).thenReturn(taskQuery);
        when(taskQuery.desc()).thenReturn(taskQuery);
        Task task = mock(Task.class);
        when(task.getId()).thenReturn("t-1");
        when(task.getName()).thenReturn("Department Approval");
        when(task.getAssignee()).thenReturn("1001");
        when(task.getCreateTime()).thenReturn(new Date());
        when(taskService.getIdentityLinksForTask("t-1")).thenReturn(List.of());
        when(taskQuery.listPage(0, 10)).thenReturn(List.of(task));

        ExternalUser alice = mock(ExternalUser.class);
        when(alice.getId()).thenReturn("1001");
        when(alice.getUsername()).thenReturn("alice.w");
        when(groupService.findUsersByIds(List.of("1001")))
                .thenReturn(List.of(alice));

        PageResult<TaskRow> page =
                service.findActiveTasks(ADMIN, "pi-1", 1, 10, null, null);

        TaskRow row = page.getRows().get(0);
        assertThat(row.getAssignee()).isEqualTo("1001");
        assertThat(row.getAssigneeUsername()).isEqualTo("alice.w");
        assertThat(row.getAssigneeDisplay()).isEqualTo("alice.w (1001)");
    }

    @Test
    void findActiveTasks_filterByTaskId() {
        asAdmin();
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.processInstanceId("pi-1")).thenReturn(taskQuery);
        when(taskQuery.active()).thenReturn(taskQuery);
        when(taskQuery.taskId("t-7")).thenReturn(taskQuery);
        when(taskQuery.count()).thenReturn(0L);

        service.findActiveTasks(ADMIN, "pi-1", 1, 10, " t-7 ", null);

        verify(taskQuery).taskId("t-7");
    }

    @Test
    void findActiveTasks_filterByCreationDate() {
        asAdmin();
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.processInstanceId("pi-1")).thenReturn(taskQuery);
        when(taskQuery.active()).thenReturn(taskQuery);
        when(taskQuery.count()).thenReturn(0L);
        when(taskQuery.taskCreatedAfter(any(Date.class))).thenReturn(taskQuery);
        when(taskQuery.taskCreatedBefore(any(Date.class))).thenReturn(taskQuery);

        service.findActiveTasks(ADMIN, "pi-1", 1, 10, null,
                newCalendarDate(2026, Calendar.MARCH, 15, 8, 0));

        verify(taskQuery).taskCreatedAfter(any(Date.class));
        verify(taskQuery).taskCreatedBefore(any(Date.class));
    }

    @Test
    void findActiveTasks_pagination_secondPage() {
        asAdmin();
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.processInstanceId("pi-1")).thenReturn(taskQuery);
        when(taskQuery.active()).thenReturn(taskQuery);
        when(taskQuery.count()).thenReturn(14L);
        when(taskQuery.orderByTaskCreateTime()).thenReturn(taskQuery);
        when(taskQuery.desc()).thenReturn(taskQuery);
        Task task = mock(Task.class);
        when(task.getId()).thenReturn("t-11");
        when(taskService.getIdentityLinksForTask("t-11")).thenReturn(List.of());
        when(taskQuery.listPage(10, 10)).thenReturn(List.of(task));

        PageResult<TaskRow> page =
                service.findActiveTasks(ADMIN, "pi-1", 2, 10, null, null);

        assertThat(page.getPageNumber()).isEqualTo(2);
        assertThat(page.getTotalRows()).isEqualTo(14L);
        verify(taskQuery).listPage(10, 10);
        assertThat(page.getRows().get(0).getCandidateGroups()).isNull();
    }

    // ------------------------------------------------------------------
    // Delegation
    // ------------------------------------------------------------------

    private Task stubActiveTask(String taskId, String assignee) {
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.taskId(taskId)).thenReturn(taskQuery);
        when(taskQuery.active()).thenReturn(taskQuery);
        Task task = mock(Task.class);
        lenient().when(task.getId()).thenReturn(taskId);
        lenient().when(task.getName()).thenReturn("Department Approval");
        lenient().when(task.getAssignee()).thenReturn(assignee);
        lenient().when(task.getProcessInstanceId()).thenReturn("pi-1");
        lenient().when(task.getTaskDefinitionKey()).thenReturn("departmentApprovalTask");
        when(taskQuery.singleResult()).thenReturn(task);
        return task;
    }

    @Test
    void delegateTask_reassignsThroughTaskServiceAndAudits() {
        asAdmin();
        stubActiveTask("t-1", "userA");

        TaskDelegationService.DelegationResult result =
                service.delegateTask(ADMIN, "t-1", "userB");

        // reassignment via the official API, nothing else is mutated
        verify(taskService).setAssignee("t-1", "userB");
        verify(taskService, never()).addCandidateGroup(anyString(), anyString());
        verify(taskService, never()).deleteCandidateGroup(anyString(), anyString());

        assertThat(result.taskId()).isEqualTo("t-1");
        assertThat(result.processInstanceId()).isEqualTo("pi-1");
        assertThat(result.previousAssignee()).isEqualTo("userA");
        assertThat(result.newAssignee()).isEqualTo("userB");

        // recorded in the existing BPM audit trail
        verify(auditService).logProcessAction(eq("pi-1"),
                eq(BpmAuditConstants.ACTION_TASK_DELEGATED),
                eq("departmentApprovalTask"), isNull(), eq(ADMIN),
                eq("userA"), contains("userB"));
    }

    @Test
    void delegateTask_missingTask_throwsObjectNotFound() {
        asAdmin();
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.taskId("gone")).thenReturn(taskQuery);
        when(taskQuery.active()).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(null);

        assertThatThrownBy(() -> service.delegateTask(ADMIN, "gone", "userB"))
                .isInstanceOf(FlowableObjectNotFoundException.class);
        verify(taskService, never()).setAssignee(anyString(), anyString());
        verifyNoInteractions(auditService);
    }

    @Test
    void delegateTask_blankArguments_rejected() {
        asAdmin();
        assertThatThrownBy(() -> service.delegateTask(ADMIN, " ", "userB"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.delegateTask(ADMIN, "t-1", null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(taskService, auditService);
    }

    @Test
    void delegateTask_auditFailure_doesNotRollbackAssignment() {
        asAdmin();
        stubActiveTask("t-1", "userA");
        doThrow(new IllegalStateException("audit DB down"))
                .when(auditService).logProcessAction(anyString(), anyString(),
                        any(), any(), anyString(), any(), anyString());

        TaskDelegationService.DelegationResult result =
                service.delegateTask(ADMIN, "t-1", "userB");

        verify(taskService).setAssignee("t-1", "userB");
        assertThat(result.newAssignee()).isEqualTo("userB");
    }

    @Test
    void delegateTask_engineFailure_propagatesWithoutAudit() {
        asAdmin();
        stubActiveTask("t-1", "userA");
        doThrow(new RuntimeException("engine down"))
                .when(taskService).setAssignee("t-1", "userB");

        assertThatThrownBy(() -> service.delegateTask(ADMIN, "t-1", "userB"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("engine down");
        verifyNoInteractions(auditService);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static IdentityLink link(String type, String groupId) {
        IdentityLink link = mock(IdentityLink.class);
        lenient().when(link.getType()).thenReturn(type);
        lenient().when(link.getGroupId()).thenReturn(groupId);
        return link;
    }

    private static Date newCalendarDate(int year, int month, int day,
                                        int hour, int minute) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(year, month, day, hour, minute);
        return calendar.getTime();
    }
}
package com.example.approval.processes.withdrawal.service;

import com.example.approval.audit.BpmAuditConstants;
import com.example.approval.audit.service.BpmAuditService;
import com.example.approval.origin.beans.StudentInfoBean;
import com.example.approval.service.CommonService;
import com.example.approval.service.ProcessStartService;
import org.flowable.engine.IdentityService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.idm.api.GroupQuery;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.Map;

import static com.example.approval.processes.withdrawal.SemesterWithdrawalConstants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the service-level guards of the Semester Withdrawal
 * process - the mirror of {@code ClearanceServiceStudentInfoTest}:
 *
 * <ul>
 *   <li><b>presubmit gate</b> (spec section 7): the SIS validation
 *       {@code checkBpmPresumbitService} runs BEFORE anything else -
 *       {@code status == 0} surfaces the returned message and the process
 *       start is never invoked; {@code status == 1} allows it;</li>
 *   <li><b>STD-only start</b>: a user outside the STD group is refused with
 *       a {@link SecurityException} - no process instance is created;</li>
 *   <li>the read-only SIS student snapshot (studentId / name / GPA /
 *       semester / gender / faculty / campus) is stored as process
 *       variables for the initiator - never taken from the client;</li>
 *   <li><b>claim audit</b> (spec section 22): claiming a candidate-group
 *       task writes the CLAIMED audit row through the shared
 *       {@link BpmAuditService}.</li>
 * </ul>
 */
class SemesterWithdrawalServiceTest {

    private static final String STUDENT = "student.test";

    private ProcessStartService processStartService;
    private IdentityService identityService;
    private TaskService taskService;
    private RuntimeService runtimeService;
    private BpmAuditService auditService;
    private CommonService commonService;
    private SemesterWithdrawalService withdrawalService;

    @BeforeEach
    void setUp() {
        processStartService = mock(ProcessStartService.class);
        identityService = mock(IdentityService.class);
        taskService = mock(TaskService.class);
        runtimeService = mock(RuntimeService.class);
        auditService = mock(BpmAuditService.class);
        commonService = mock(CommonService.class);
        withdrawalService = new SemesterWithdrawalService(processStartService, identityService,
                taskService, runtimeService, auditService, commonService);
    }

    /** Stubs the identity group query chain so the user is/is not a member of STD. */
    private void allowStart(String userId, boolean member) {
        GroupQuery groupQuery = mock(GroupQuery.class);
        when(identityService.createGroupQuery()).thenReturn(groupQuery);
        when(groupQuery.groupMember(anyString())).thenReturn(groupQuery);
        when(groupQuery.groupId(anyString())).thenReturn(groupQuery);
        when(groupQuery.count()).thenReturn(member ? 1L : 0L);
    }

    private StudentInfoBean studentInfo() {
        StudentInfoBean sis = new StudentInfoBean();
        sis.setStudentId("S-123");
        sis.setStudentName("Test Student");
        sis.setCumStudentGPA("3.55");
        sis.setAcademicYear("2025/2026");
        sis.setGender("F");
        sis.setFacultyNo("10");
        sis.setCampusNo("1");
        return sis;
    }

    // ------------------------------------------------------------------
    // Presubmit validation (spec section 7)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("presubmit status == 1 -> allowed")
    void presubmit_statusOne_isAllowed() {
        when(commonService.getStudentInfo(STUDENT)).thenReturn(studentInfo());
        when(commonService.checkBpmPresumbitService(anyMap()))
                .thenReturn(Map.of("status", 1, "msg", "OK"));

        SemesterWithdrawalService.PresubmitResult result =
                withdrawalService.checkPresubmit(STUDENT, "20261", "2");

        assertTrue(result.isAllowed());
        assertEquals(1, result.status());
    }

    @Test
    @DisplayName("presubmit status == 0 -> rejected with the returned SIS message")
    void presubmit_statusZero_returnsSisMessage() {
        when(commonService.getStudentInfo(STUDENT)).thenReturn(studentInfo());
        when(commonService.checkBpmPresumbitService(anyMap()))
                .thenReturn(Map.of("status", 0, "msg", "Student has an active withdrawal request"));

        SemesterWithdrawalService.PresubmitResult result =
                withdrawalService.checkPresubmit(STUDENT, "20261", "2");

        assertFalse(result.isAllowed());
        assertEquals("Student has an active withdrawal request", result.msg());
    }

    @Test
    @DisplayName("presubmit passes the student/semester/documentCode to the SIS procedure")
    void presubmit_passesCorrectParameters() {
        when(commonService.getStudentInfo(STUDENT)).thenReturn(studentInfo());

        withdrawalService.checkPresubmit(STUDENT, "20261", "1");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor =
                ArgumentCaptor.forClass((Class<Map<String, Object>>) (Class<?>) Map.class);
        verify(commonService).checkBpmPresumbitService(captor.capture());
        Map<String, Object> params = captor.getValue();
        assertEquals("S-123", params.get("studentId"));
        assertEquals("20261", params.get("semester"));
        assertEquals(SemesterWithdrawalService.PRESUBMIT_DOCUMENT_CODE,
                params.get("documentCode"));
        assertEquals("1", params.get("lang"));
    }

    // ------------------------------------------------------------------
    // Start authorization + SIS snapshot variables
    // ------------------------------------------------------------------

    @Test
    @DisplayName("STD member may start; the SIS snapshot becomes the process variables")
    void startWithdrawal_stdMember_storesStudentSnapshotAsVariables() {
        allowStart(STUDENT, true);
        when(commonService.getStudentInfo(STUDENT)).thenReturn(studentInfo());

        Map<String, Object> formVariables = new HashMap<>();
        formVariables.put(VAR_WITHDRAWAL_REASON, "R1");
        formVariables.put(VAR_WITHDRAWAL_SEMESTER, "20261");
        formVariables.put(VAR_STUDENT_NOTE, "personal reasons");
        withdrawalService.startWithdrawal(STUDENT, formVariables);

        verify(processStartService).startProcess(eq(PROCESS_KEY), eq(STUDENT), anyMap());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor =
                ArgumentCaptor.forClass((Class<Map<String, Object>>) (Class<?>) Map.class);
        verify(processStartService).startProcess(anyString(), anyString(), captor.capture());
        Map<String, Object> vars = captor.getValue();

        assertEquals(STUDENT, vars.get(VAR_INITIATOR));
        assertEquals("S-123", vars.get(VAR_STUDENT_ID));
        assertEquals("Test Student", vars.get(VAR_STUDENT_NAME));
        assertEquals("3.55", vars.get(VAR_STUDENT_GPA));
        assertEquals("2025/2026", vars.get(VAR_CURRENT_SEMESTER));
        assertEquals("F", vars.get(VAR_GENDER));
        assertEquals("10", vars.get(VAR_FACULTY_NO));
        assertEquals("1", vars.get(VAR_CAMPUS_NO));
        assertEquals("R1", vars.get(VAR_WITHDRAWAL_REASON));
        assertEquals("20261", vars.get(VAR_WITHDRAWAL_SEMESTER));
        assertEquals("personal reasons", vars.get(VAR_STUDENT_NOTE));
    }

    @Test
    @DisplayName("non-STD user cannot start - SecurityException, no process instance")
    void startWithdrawal_notStudentGroup_refused() {
        allowStart(STUDENT, false);

        Map<String, Object> formVariables = new HashMap<>();
        formVariables.put(VAR_WITHDRAWAL_REASON, "R1");
        formVariables.put(VAR_WITHDRAWAL_SEMESTER, "20261");

        assertThrows(SecurityException.class,
                () -> withdrawalService.startWithdrawal(STUDENT, formVariables));
        verify(processStartService, never()).startProcess(anyString(), anyString(), anyMap());
        verify(commonService, never()).getStudentInfo(anyString());
    }

    @Test
    @DisplayName("no SIS student row - start refused")
    void startWithdrawal_noStudentInfo_refused() {
        allowStart(STUDENT, true);
        when(commonService.getStudentInfo(STUDENT)).thenReturn(null);

        assertThrows(IllegalStateException.class,
                () -> withdrawalService.startWithdrawal(STUDENT, new HashMap<>()));
        verify(processStartService, never()).startProcess(anyString(), anyString(), anyMap());
    }

    // ------------------------------------------------------------------
    // Presubmit failure must prevent the start (spec: never create the
    // process before successful validation)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("status == 0 presubmit result must keep the caller from starting (contract)")
    void presubmitGate_contract_statusZeroPreventsStart() {
        when(commonService.getStudentInfo(STUDENT)).thenReturn(studentInfo());
        when(commonService.checkBpmPresumbitService(anyMap()))
                .thenReturn(Map.of("status", 0, "msg", "not allowed"));

        // the JSF submit() flow: checkPresubmit first; isAllowed() == false
        // means startWithdrawal is never called (verified by the bean logic)
        SemesterWithdrawalService.PresubmitResult result =
                withdrawalService.checkPresubmit(STUDENT, "20261", "2");
        if (result.isAllowed()) {
            withdrawalService.startWithdrawal(STUDENT, new HashMap<>());
        }
        verify(processStartService, never()).startProcess(anyString(), anyString(), anyMap());
    }

    // ------------------------------------------------------------------
    // Claim audit (spec section 22)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("claiming a group task writes the CLAIMED audit row")
    void claimTask_writesClaimedAuditRow() {
        Task task = mock(Task.class);
        when(task.getId()).thenReturn("task-1");
        when(task.getTaskDefinitionKey()).thenReturn(TASK_REG_APPROVAL);
        when(task.getProcessInstanceId()).thenReturn("proc-1");
        when(task.getAssignee()).thenReturn(null);
        TaskQuery taskQuery = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(task);
        when(runtimeService.getVariable("proc-1", VAR_APPROVAL_GROUP)).thenReturn(null);

        withdrawalService.claimTask("task-1", STUDENT);

        verify(taskService).claim("task-1", STUDENT);
        verify(auditService).logProcessAction(eq("proc-1"),
                eq(BpmAuditConstants.ACTION_TASK_CLAIMED),
                eq(STAGE_ADMISSION_AND_REGISTRATION),
                eq(DEPT_ADMISSION_AND_REGISTRATION),
                eq(STUDENT), isNull(), anyString());
    }

    @Test
    @DisplayName("claim of a task already claimed by someone else is refused")
    void claimTask_alreadyClaimedByOther_refused() {
        Task task = mock(Task.class);
        when(task.getId()).thenReturn("task-1");
        when(task.getAssignee()).thenReturn("someone.else");
        TaskQuery taskQuery = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(task);

        assertThrows(IllegalStateException.class,
                () -> withdrawalService.claimTask("task-1", STUDENT));
        verify(taskService, never()).claim(anyString(), anyString());
    }

    @Test
    @DisplayName("approval completion requires a valid decision value")
    void completeApprovalTask_invalidDecision_refused() {
        Task task = mock(Task.class);
        when(task.getId()).thenReturn("task-1");
        when(task.getAssignee()).thenReturn(STUDENT);
        TaskQuery taskQuery = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(task);

        assertThrows(IllegalArgumentException.class,
                () -> withdrawalService.completeApprovalTask("task-1", "maybe", null, STUDENT));
        verify(taskService, never()).complete(anyString(), anyMap());
    }

    // ------------------------------------------------------------------
    // Process instance start passthrough
    // ------------------------------------------------------------------

    @Test
    @DisplayName("startWithdrawal uses the shared ProcessStartService with the exact process key")
    void startWithdrawal_usesProcessStartServiceWithExactKey() {
        allowStart(STUDENT, true);
        when(commonService.getStudentInfo(STUDENT)).thenReturn(studentInfo());
        ProcessInstance instance = mock(ProcessInstance.class);
        when(instance.getId()).thenReturn("proc-9");
        when(processStartService.startProcess(eq(PROCESS_KEY), eq(STUDENT), anyMap()))
                .thenReturn(instance);

        ProcessInstance started = withdrawalService.startWithdrawal(STUDENT, new HashMap<>());

        assertEquals("proc-9", started.getId());
        verify(processStartService).startProcess(eq("semester-withdrawl"), eq(STUDENT), anyMap());
    }
}
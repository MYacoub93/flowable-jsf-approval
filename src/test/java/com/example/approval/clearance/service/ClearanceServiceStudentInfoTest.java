package com.example.approval.clearance.service;

import com.example.approval.audit.service.BpmAuditService;
import com.example.approval.origin.beans.StudentInfoBean;
import com.example.approval.processes.clearance.service.ClearanceService;
import com.example.approval.service.CommonService;
import com.example.approval.service.ProcessStartService;
import org.flowable.engine.IdentityService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.idm.api.GroupQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.Map;

import static com.example.approval.processes.clearance.ClearanceConstants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the SIS student-information snapshot behaviour of
 * {@link ClearanceService#startClearance(String, Map)}:
 *
 * <ul>
 *   <li>the read-only student fields are loaded from the SIS via
 *       {@code CommonService.getStudentInfo(username)} for the <b>initiator</b>,</li>
 *   <li>they are stored as process variables (studentId, studentName,
 *       studentEmail, studentGPA, studentMobile, academicYear) together with
 *       the existing notes value,</li>
 *   <li>no SIS row - the start is refused.</li>
 * </ul>
 *
 * <p>Later Clearance tasks read these variables instead of re-querying the
 * SIS with the (department employee's) task assignee username - see
 * {@code ClearanceTaskBean}.</p>
 */
class ClearanceServiceStudentInfoTest {

    private static final String STUDENT = "student.test";
    private static final String NOTES = "Please process my clearance";

    private ProcessStartService processStartService;
    private IdentityService identityService;
    private CommonService commonService;
    private ClearanceService clearanceService;

    @BeforeEach
    void setUp() {
        processStartService = mock(ProcessStartService.class);
        identityService = mock(IdentityService.class);
        commonService = mock(CommonService.class);
        clearanceService = new ClearanceService(processStartService, identityService,
                mock(TaskService.class), mock(RuntimeService.class),
                mock(BpmAuditService.class), commonService);
    }

    /** Stubs the identity group query chain so the user is a member of STD. */
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
        sis.setEmail("student@example.com");
        sis.setCumStudentGPA("3.55");
        sis.setMobile("+962790000000");
        sis.setAcademicYear("2025/2026");
        return sis;
    }

    @Test
    @DisplayName("startClearance stores the SIS student snapshot + notes as process variables")
    void startClearance_storesStudentInfoAsProcessVariables() {
        allowStart(STUDENT, true);
        when(commonService.getStudentInfo(STUDENT)).thenReturn(studentInfo());

        Map<String, Object> formVariables = new HashMap<>();
        formVariables.put(VAR_NOTES, NOTES);
        formVariables.put(VAR_PROGRAM, "BSc Computer Science");

        clearanceService.startClearance(STUDENT, formVariables);

        // the SIS was queried with the INITIATOR's username (exactly once)
        verify(commonService).getStudentInfo(STUDENT);

        // the passed variables contain the SIS snapshot + existing notes
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor =
                ArgumentCaptor.forClass((Class<Map<String, Object>>) (Class<?>) Map.class);
        verify(processStartService).startProcess(anyString(), anyString(), captor.capture());
        Map<String, Object> vars = captor.getValue();

        assertEquals("S-123", vars.get(VAR_STUDENT_ID));
        assertEquals("Test Student", vars.get(VAR_STUDENT_NAME));
        assertEquals("student@example.com", vars.get(VAR_STUDENT_EMAIL));
        assertEquals("3.55", vars.get(VAR_STUDENT_GPA));
        assertEquals("+962790000000", vars.get(VAR_STUDENT_MOBILE));
        assertEquals("2025/2026", vars.get(VAR_ACADEMIC_YEAR));
        assertEquals(NOTES, vars.get(VAR_NOTES));
        assertEquals(STUDENT, vars.get(VAR_INITIATOR));
    }

    @Test
    @DisplayName("startClearance refuses to start when the SIS returns no student row")
    void startClearance_noStudentInfo_refusesToStart() {
        allowStart(STUDENT, true);
        when(commonService.getStudentInfo(STUDENT)).thenReturn(null);

        Map<String, Object> formVariables = new HashMap<>();
        formVariables.put(VAR_NOTES, NOTES);

        assertThrows(IllegalStateException.class,
                () -> clearanceService.startClearance(STUDENT, formVariables));
    }

    @Test
    @DisplayName("startClearance refuses to start for a non-STD user")
    void startClearance_notStudentGroup_refusesToStart() {
        allowStart(STUDENT, false);

        Map<String, Object> formVariables = new HashMap<>();
        formVariables.put(VAR_NOTES, NOTES);

        assertThrows(SecurityException.class,
                () -> clearanceService.startClearance(STUDENT, formVariables));
    }
}
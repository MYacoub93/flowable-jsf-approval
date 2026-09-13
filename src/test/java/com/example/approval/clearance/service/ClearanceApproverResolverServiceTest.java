package com.example.approval.clearance.service;

import com.example.approval.processes.clearance.service.ClearanceApproverResolverService;
import com.example.approval.service.CommonService;
import com.example.approval.service.FlowableIdentityService;
import org.flowable.idm.engine.impl.persistence.entity.UserEntityImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ClearanceApproverResolverService} - the WEB_NAME to
 * Flowable user id translation behind the HOD / dean single-approver
 * assignment. All SIS / identity collaborators are mocked (no Oracle).
 */
@ExtendWith(MockitoExtension.class)
class ClearanceApproverResolverServiceTest {

    private static final String FACULTY_NO = "12";
    private static final String DEPT_NO = "34";
    private static final String CAMPUS_NO = "1";

    @Mock
    private CommonService commonService;
    @Mock
    private FlowableIdentityService identityService;

    private ClearanceApproverResolverService service;

    @BeforeEach
    void setUp() {
        service = new ClearanceApproverResolverService(commonService, identityService);
    }

    // ------------------------------------------------------------------
    // happy path: HOD / DEN resolve to the FLOWABLE_USERS_VW user id
    // ------------------------------------------------------------------

    @Test
    @DisplayName("HOD: getHeadOfDepartment WEB_NAME is translated into the user id")
    void hodResolvesToUserId() {
        when(commonService.getHeadOfDepartment(FACULTY_NO, DEPT_NO, CAMPUS_NO))
                .thenReturn("prof.smith");
        when(identityService.findUserByUsername("prof.smith"))
                .thenReturn(user("555"));

        assertThat(service.resolveSingleApproverId("HOD", FACULTY_NO, DEPT_NO, CAMPUS_NO))
                .isEqualTo("555");
    }

    @Test
    @DisplayName("DEN: getDeanOfCollege WEB_NAME is translated into the user id")
    void deanResolvesToUserId() {
        when(commonService.getDeanOfCollege(FACULTY_NO, CAMPUS_NO))
                .thenReturn("dean.jones");
        when(identityService.findUserByUsername("dean.jones"))
                .thenReturn(user("777"));

        assertThat(service.resolveSingleApproverId("DEN", FACULTY_NO, DEPT_NO, CAMPUS_NO))
                .isEqualTo("777");
    }

    @Test
    @DisplayName("WEB_NAME with surrounding blanks is trimmed before the lookup")
    void webNameIsTrimmed() {
        when(commonService.getDeanOfCollege(FACULTY_NO, CAMPUS_NO))
                .thenReturn("  dean.jones  ");
        when(identityService.findUserByUsername("dean.jones"))
                .thenReturn(user("777"));

        assertThat(service.resolveSingleApproverId("DEN", FACULTY_NO, DEPT_NO, CAMPUS_NO))
                .isEqualTo("777");
    }

    // ------------------------------------------------------------------
    // every other department stays a candidate-group task
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Non-HOD/DEN departments resolve to null without any SIS lookup")
    void otherDepartmentsStayGroupTasks() {
        assertThat(service.resolveSingleApproverId("IT", FACULTY_NO, DEPT_NO, CAMPUS_NO))
                .isNull();
        assertThat(service.resolveSingleApproverId("LIB", FACULTY_NO, DEPT_NO, CAMPUS_NO))
                .isNull();
        verifyNoInteractions(commonService, identityService);
    }

    // ------------------------------------------------------------------
    // best effort: every gap resolves to null, never an exception
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Missing SIS keys (null facultyNo/deptNo) resolve to null")
    void missingSisKeysResolveToNull() {
        assertThat(service.resolveSingleApproverId("HOD", null, DEPT_NO, CAMPUS_NO)).isNull();
        assertThat(service.resolveSingleApproverId("HOD", FACULTY_NO, null, CAMPUS_NO)).isNull();
        assertThat(service.resolveSingleApproverId("HOD", " ", DEPT_NO, CAMPUS_NO)).isNull();
        assertThat(service.resolveSingleApproverId("DEN", null, DEPT_NO, CAMPUS_NO)).isNull();
        verifyNoInteractions(identityService);
    }

    @Test
    @DisplayName("No HOD / dean row in the SIS resolves to null")
    void missingHodOrDeanResolvesToNull() {
        when(commonService.getHeadOfDepartment(FACULTY_NO, DEPT_NO, CAMPUS_NO))
                .thenReturn(null);
        when(commonService.getDeanOfCollege(FACULTY_NO, CAMPUS_NO))
                .thenReturn("   ");

        assertThat(service.resolveSingleApproverId("HOD", FACULTY_NO, DEPT_NO, CAMPUS_NO))
                .isNull();
        assertThat(service.resolveSingleApproverId("DEN", FACULTY_NO, DEPT_NO, CAMPUS_NO))
                .isNull();
        verify(identityService, never()).findUserByUsername(anyString());
    }

    @Test
    @DisplayName("WEB_NAME without a FLOWABLE_USERS_VW entry resolves to null")
    void unknownWebNameResolvesToNull() {
        when(commonService.getDeanOfCollege(FACULTY_NO, CAMPUS_NO))
                .thenReturn("ghost.user");
        when(identityService.findUserByUsername("ghost.user"))
                .thenReturn(null);

        assertThat(service.resolveSingleApproverId("DEN", FACULTY_NO, DEPT_NO, CAMPUS_NO))
                .isNull();
    }

    @Test
    @DisplayName("null department resolves to null")
    void nullDepartmentResolvesToNull() {
        assertThat(service.resolveSingleApproverId(null, FACULTY_NO, DEPT_NO, CAMPUS_NO))
                .isNull();
        verifyNoInteractions(commonService, identityService);
    }

    // ------------------------------------------------------------------
    // helper
    // ------------------------------------------------------------------

    private static UserEntityImpl user(String id) {
        UserEntityImpl user = new UserEntityImpl();
        user.setId(id);
        return user;
    }
}
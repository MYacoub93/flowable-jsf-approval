package com.example.approval.backing;

import com.example.approval.entity.AuthenticatedUser;
import com.example.approval.origin.beans.StaffInfoBean;
import com.example.approval.origin.beans.StudentInfoBean;
import com.example.approval.service.CommonService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the login-time population of {@link StudentInfoBean} with the
 * default role and role code coming from FLOWABLE_USERS_VW
 * (DEFAULT_ROLE_ / ROLE_CODE_), and the role-based session profile
 * (student vs staff SIS information) stored in {@link SessionInfoBean}.
 *
 * <ul>
 *   <li>{@link UserLoginBean#studentInfoOf} copies every identity column and
 *       both role columns onto the snapshot bean.</li>
 *   <li>NULL role values must not fail anything - login keeps working.</li>
 *   <li>The extended mapper query still selects the two role columns
 *       (guards against accidental removal from
 *       FlowableIdentityMapper.xml).</li>
 *   <li>{@code DEFAULT_ROLE_ == "STD"} loads the student SIS profile
 *       ({@code CommonMapper.getStudentInfo}); anything else (including
 *       null) loads the staff SIS profile ({@code CommonMapper.getStaffInfo})
 *       into {@link SessionInfoBean}, never failing the login.</li>
 * </ul>
 */
class UserLoginBeanStudentInfoTest {

    private AuthenticatedUser authenticatedUser() {
        AuthenticatedUser user = new AuthenticatedUser();
        user.setId("123456");
        user.setUsername("john.doe");
        user.setFirstName("John");
        user.setLastName("Doe");
        user.setEmail("john.doe@example.edu");
        return user;
    }

    /**
     * Stub of the Spring service: records which profile lookups were
     * requested and returns canned results or failures. Overriding the two
     * lookup methods keeps the (mapper-backed) inherited ones from ever
     * touching a database.
     */
    private static final class StubCommonService extends CommonService {
        final List<String> studentRequests = new ArrayList<>();
        final List<String> staffRequests = new ArrayList<>();
        StudentInfoBean studentResult;
        StaffInfoBean staffResult;
        RuntimeException studentFailure;
        RuntimeException staffFailure;

        @Override
        public StudentInfoBean getStudentInfo(String userName) {
            studentRequests.add(userName);
            if (studentFailure != null) {
                throw studentFailure;
            }
            return studentResult;
        }

        @Override
        public StaffInfoBean getStaffInfo(String userName) {
            staffRequests.add(userName);
            if (staffFailure != null) {
                throw staffFailure;
            }
            return staffResult;
        }
    }

    private StudentInfoBean sisStudent(String id, String name) {
        StudentInfoBean bean = new StudentInfoBean();
        bean.setStudentId(id);
        bean.setStudentName(name);
        return bean;
    }

    private StaffInfoBean sisStaff(String instructorId, String name) {
        StaffInfoBean bean = new StaffInfoBean();
        bean.setInstructorId(instructorId);
        bean.setInstructorName(name);
        return bean;
    }

    // --- StudentInfoBean snapshot (unchanged login-row behavior) ----------

    @Test
    @DisplayName("studentInfoOf copies identity columns and both role values")
    void copiesIdentityAndRoleValues() {
        AuthenticatedUser user = authenticatedUser();
        user.setDefaultRole("Registrar");
        user.setRoleCode("REG");

        StudentInfoBean info = UserLoginBean.studentInfoOf(user, "john.doe");

        assertNotNull(info);
        // Existing identity information must remain unchanged.
        assertEquals("123456", info.getStudentId());
        assertEquals("john.doe", info.getUserName());
        assertEquals("John", info.getFirstName());
        assertEquals("Doe", info.getLastName());
        assertEquals("john.doe@example.edu", info.getEmail());
        // New role fields from FLOWABLE_USERS_VW.
        assertEquals("Registrar", info.getDefaultRole());
        assertEquals("REG", info.getRoleCode());
    }

    @Test
    @DisplayName("NULL default_role_ / role_code_ never break the snapshot")
    void nullRoleValuesAreKeptAsNull() {
        AuthenticatedUser user = authenticatedUser();
        user.setDefaultRole(null);
        user.setRoleCode(null);

        StudentInfoBean info = UserLoginBean.studentInfoOf(user, "john.doe");

        assertNotNull(info);
        assertNull(info.getDefaultRole());
        assertNull(info.getRoleCode());
        // The rest of the user information is still populated normally.
        assertEquals("123456", info.getStudentId());
        assertEquals("john.doe@example.edu", info.getEmail());
    }

    @Test
    @DisplayName("a null user maps to a null snapshot (login already rejected it)")
    void nullUserYieldsNullSnapshot() {
        assertNull(UserLoginBean.studentInfoOf(null, "john.doe"));
    }

    @Test
    @DisplayName("the login mapper query selects default_role_ and role_code_")
    void mapperQueryContainsRoleColumns() throws Exception {
        String xml;
        try (InputStream in = getClass().getResourceAsStream("/mapper/FlowableIdentityMapper.xml")) {
            assertNotNull(in, "FlowableIdentityMapper.xml must be on the test classpath");
            xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        int selectStart = xml.indexOf("findUserByUsernameForAuth");
        assertTrue(selectStart >= 0, "findUserByUsernameForAuth must exist");
        String selectBody = xml.substring(selectStart, xml.indexOf("</select>", selectStart));

        assertTrue(selectBody.contains("default_role_"),
                "login query must select default_role_");
        assertTrue(selectBody.contains("role_code_"),
                "login query must select role_code_");
        assertTrue(selectBody.contains("FLOWABLE_USERS_VW"),
                "login query must stay on FLOWABLE_USERS_VW");
        assertTrue(selectBody.contains("AuthenticatedUser"),
                "login query must map onto the AuthenticatedUser carrier");
        // Authentication logic must be preserved untouched.
        assertTrue(selectBody.contains("decrypt_data"),
                "password decryption must stay in the login query");
        assertTrue(selectBody.contains("WHERE u.USERNAME_ = LOWER(#{username})"),
                "username WHERE clause must stay unchanged");
    }

    // --- Role-based session profile ----------------------------------------

    @Test
    @DisplayName("the student/staff business rule: only exactly \"STD\" is a student")
    void isStudentRule() {
        assertTrue(UserLoginBean.isStudent("STD"), "STD marks a student");
        assertFalse(UserLoginBean.isStudent(null), "null role means staff");
        assertFalse(UserLoginBean.isStudent(""), "empty role means staff");
        assertFalse(UserLoginBean.isStudent("REG"), "other codes mean staff");
        assertFalse(UserLoginBean.isStudent("std"), "the rule is case-sensitive");
        assertFalse(UserLoginBean.isStudent(" STD "), "the rule matches the exact value");
    }

    @Test
    @DisplayName("student login (STD) stores the SIS student profile with the role fields")
    void studentLoginLoadsStudentProfile() {
        AuthenticatedUser user = authenticatedUser();
        user.setDefaultRole("STD");
        user.setRoleCode("ST");
        StubCommonService service = new StubCommonService();
        service.studentResult = sisStudent("123456", "John Doe");

        SessionInfoBean sessionInfo = new SessionInfoBean();
        UserLoginBean.loadRoleProfileInto(sessionInfo, user, "john.doe", service);

        assertEquals(List.of("john.doe"), service.studentRequests,
                "the student lookup must be keyed on the web username");
        assertTrue(service.staffRequests.isEmpty(), "staff lookup must not run for a student");
        assertNotNull(sessionInfo.getStudentInfoBean());
        assertEquals("123456", sessionInfo.getStudentInfoBean().getStudentId());
        // Login-row role fields are applied onto the SIS profile.
        assertEquals("STD", sessionInfo.getStudentInfoBean().getDefaultRole());
        assertEquals("ST", sessionInfo.getStudentInfoBean().getRoleCode());
        assertNull(sessionInfo.getStaffInfoBean(), "staff profile must stay null for a student");
    }

    @Test
    @DisplayName("staff login stores the SIS staff profile and nulls the student profile")
    void staffLoginLoadsStaffProfile() {
        AuthenticatedUser user = authenticatedUser();
        user.setDefaultRole("Registrar");
        user.setRoleCode("REG");
        StubCommonService service = new StubCommonService();
        service.staffResult = sisStaff("990011", "Ahmad Ali");

        SessionInfoBean sessionInfo = new SessionInfoBean();
        UserLoginBean.loadRoleProfileInto(sessionInfo, user, "john.doe", service);

        assertEquals(List.of("john.doe"), service.staffRequests,
                "the staff lookup must be keyed on the web username");
        assertTrue(service.studentRequests.isEmpty(), "student lookup must not run for staff");
        assertNotNull(sessionInfo.getStaffInfoBean());
        assertEquals("990011", sessionInfo.getStaffInfoBean().getInstructorId());
        assertNull(sessionInfo.getStudentInfoBean(), "student profile must stay null for staff");
    }

    @Test
    @DisplayName("a NULL default_role_ follows the staff path (login still succeeds)")
    void nullRoleMeansStaff() {
        AuthenticatedUser user = authenticatedUser();
        user.setDefaultRole(null);
        user.setRoleCode(null);
        StubCommonService service = new StubCommonService();
        service.staffResult = sisStaff("990011", "Ahmad Ali");

        SessionInfoBean sessionInfo = new SessionInfoBean();
        UserLoginBean.loadRoleProfileInto(sessionInfo, user, "john.doe", service);

        assertTrue(service.studentRequests.isEmpty(), "null role must not take the student path");
        assertNotNull(sessionInfo.getStaffInfoBean(),
                "null role loads the staff profile");
    }

    @Test
    @DisplayName("the SIS lookup prefers the login row's USERNAME_ and falls back to the login name")
    void webNamePrefersUsername() {
        AuthenticatedUser user = authenticatedUser();
        user.setUsername("j.doe.web"); // USERNAME_ of the login row
        user.setDefaultRole("STD");
        StubCommonService service = new StubCommonService();
        service.studentResult = sisStudent("123456", "John Doe");

        SessionInfoBean sessionInfo = new SessionInfoBean();
        UserLoginBean.loadRoleProfileInto(sessionInfo, user, "j.doe", service);

        assertEquals(List.of("j.doe.web"), service.studentRequests,
                "USERNAME_ (WEB_NAME) must be preferred over the raw login name");

        // ... and the fallback when the row carries no username.
        user.setUsername(null);
        UserLoginBean.loadRoleProfileInto(sessionInfo, user, "j.doe", service);
        assertEquals("j.doe", service.studentRequests.get(1),
                "the login name is used when USERNAME_ is missing");
    }

    @Test
    @DisplayName("a missing SIS record (null result) keeps the session profile null - login unaffected")
    void noSisRecordKeepsLoginWorking() {
        AuthenticatedUser user = authenticatedUser();
        user.setDefaultRole("STD");
        StubCommonService service = new StubCommonService();
        service.studentResult = null; // no sis_students row for this user

        SessionInfoBean sessionInfo = new SessionInfoBean();
        UserLoginBean.loadRoleProfileInto(sessionInfo, user, "john.doe", service);

        assertNull(sessionInfo.getStudentInfoBean(),
                "no SIS record simply leaves the profile null");
        assertNull(sessionInfo.getStaffInfoBean());
    }

    @Test
    @DisplayName("a SIS lookup failure never propagates - both profiles are just cleared")
    void serviceFailureNeverFailsLogin() {
        AuthenticatedUser user = authenticatedUser();
        user.setDefaultRole("STD");
        StubCommonService service = new StubCommonService();
        service.studentFailure = new RuntimeException("SIS down");

        SessionInfoBean sessionInfo = new SessionInfoBean();
        // Must not throw - the login flow stays untouched.
        UserLoginBean.loadRoleProfileInto(sessionInfo, user, "john.doe", service);

        assertNull(sessionInfo.getStudentInfoBean());
        assertNull(sessionInfo.getStaffInfoBean());
    }

    @Test
    @DisplayName("a role switch between logins never leaves a stale profile behind")
    void staleProfilesClearedOnNewLogin() {
        // Previous session state: a student was logged in.
        SessionInfoBean sessionInfo = new SessionInfoBean();
        sessionInfo.setStudentInfoBean(sisStudent("123456", "John Doe"));

        // The same session now logs in a staff member.
        AuthenticatedUser staffUser = authenticatedUser();
        staffUser.setDefaultRole("Registrar");
        StubCommonService service = new StubCommonService();
        service.staffResult = sisStaff("990011", "Ahmad Ali");

        UserLoginBean.loadRoleProfileInto(sessionInfo, staffUser, "john.doe", service);

        assertNull(sessionInfo.getStudentInfoBean(),
                "the stale student profile must be gone");
        assertNotNull(sessionInfo.getStaffInfoBean());

        // ... and clear() (logout) drops both profiles as well.
        sessionInfo.clear();
        assertNull(sessionInfo.getStudentInfoBean());
        assertNull(sessionInfo.getStaffInfoBean());
    }

    @Test
    @DisplayName("applyLoginRow only fills a blank userName and never overwrites SIS data")
    void applyLoginRowFillsBlankUserNameOnly() {
        AuthenticatedUser user = authenticatedUser();
        user.setDefaultRole("STD");
        user.setRoleCode("ST");

        StudentInfoBean blank = sisStudent("123456", "John Doe"); // userName is null
        UserLoginBean.applyLoginRow(blank, user, "john.doe");
        assertEquals("john.doe", blank.getUserName(), "blank userName is filled from the login");
        assertEquals("STD", blank.getDefaultRole());
        assertEquals("ST", blank.getRoleCode());

        StudentInfoBean named = sisStudent("123456", "John Doe");
        named.setUserName("sis.web.name"); // already set by the SIS query
        UserLoginBean.applyLoginRow(named, user, "john.doe");
        assertEquals("sis.web.name", named.getUserName(), "an existing userName is kept");

        // Null profile (no SIS record) is a harmless no-op.
        UserLoginBean.applyLoginRow(null, user, "john.doe");
    }

    // --- Guards against accidental mapper regressions ---------------------

    @Test
    @DisplayName("CommonMapper keeps the student and staff profile queries used at login")
    void commonMapperKeepsProfileQueries() throws Exception {
        String xml;
        try (InputStream in = getClass().getResourceAsStream("/mapper/CommonMapper.xml")) {
            assertNotNull(in, "CommonMapper.xml must be on the test classpath");
            xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(xml.contains("id=\"getStudentInfo\""),
                "getStudentInfo must stay in CommonMapper.xml");
        assertTrue(xml.contains("id=\"getStaffInfo\""),
                "getStaffInfo must stay in CommonMapper.xml");
        assertTrue(xml.toLowerCase().contains("web_name"),
                "both queries are keyed on the SIS web username (WEB_NAME)");
    }
}
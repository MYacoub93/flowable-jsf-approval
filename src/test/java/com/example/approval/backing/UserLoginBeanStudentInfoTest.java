package com.example.approval.backing;

import com.example.approval.entity.AuthenticatedUser;
import com.example.approval.origin.beans.StudentInfoBean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the login-time population of {@link StudentInfoBean} with the
 * default role and role code coming from FLOWABLE_USERS_VW
 * (DEFAULT_ROLE_ / ROLE_CODE_).
 *
 * <ul>
 *   <li>{@link UserLoginBean#studentInfoOf} copies every identity column and
 *       both role columns onto the snapshot bean.</li>
 *   <li>NULL role values must not fail anything - login keeps working.</li>
 *   <li>The extended mapper query still selects the two role columns
 *       (guards against accidental removal from
 *       FlowableIdentityMapper.xml).</li>
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
}
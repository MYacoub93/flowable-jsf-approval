package com.example.approval.service;

import com.example.approval.entity.ExternalUser;
import com.example.approval.entity.GroupMembership;
import com.example.approval.entity.PageResult;
import com.example.approval.entity.WebRole;
import com.example.approval.mapper.ExternalGroupMapper;
import org.flowable.engine.IdentityService;
import org.flowable.idm.api.GroupQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for the ExternalGroupService (mapper + IdentityService mocked). */
@ExtendWith(MockitoExtension.class)
class ExternalGroupServiceTest {

    @Mock
    private ExternalGroupMapper mapper;

    @Mock
    private IdentityService identityService;

    @Mock
    private GroupQuery groupQuery;

    private ExternalGroupService service;

    @BeforeEach
    void setUp() {
        service = new ExternalGroupService(mapper, identityService);
    }

    private void asAdmin(String userId) {
        when(identityService.createGroupQuery()).thenReturn(groupQuery);
        when(groupQuery.groupMember(userId)).thenReturn(groupQuery);
        when(groupQuery.groupId(ExternalGroupService.ADMIN_ROLE_CODE)).thenReturn(groupQuery);
        when(groupQuery.count()).thenReturn(1L);
    }

    @Test
    void isGroupAdmin_falseForNullUser() {
        assertThat(service.isGroupAdmin(null)).isFalse();
        assertThat(service.isGroupAdmin("  ")).isFalse();
    }

    @Test
    void createRole_rejectsNonAdmin() {
        when(identityService.createGroupQuery()).thenReturn(groupQuery); when(groupQuery.groupMember("somebody")).thenReturn(groupQuery); when(groupQuery.groupId(ExternalGroupService.ADMIN_ROLE_CODE)).thenReturn(groupQuery); when(groupQuery.count()).thenReturn(0L);
        WebRole role = validRole();
        assertThatThrownBy(() -> service.createRole(role, null, "somebody"))
                .isInstanceOf(SecurityException.class);
        verify(mapper, never()).insertRole(any());
    }

    private static WebRole validRole() {
        WebRole role = new WebRole();
        role.setRoleCode("  TEST_ROLE  ");
        role.setRoleName("  Test Role  ");
        return role;
    }

    @Test
    void createRole_trimsAndPersistsWithGeneratedId() {
        asAdmin("admin");
        when(mapper.findRoleByCode("TEST_ROLE")).thenReturn(null);
        when(mapper.nextRoleId()).thenReturn(42L);
        when(mapper.findNumericUserId("admin")).thenReturn(7L);

        WebRole saved = service.createRole(validRole(), null, "admin");

        assertThat(saved.getRoleId()).isEqualTo(42L);
        assertThat(saved.getRoleCode()).isEqualTo("TEST_ROLE");
        assertThat(saved.getRoleName()).isEqualTo("Test Role");
        assertThat(saved.getCreatedBy()).isEqualTo(7L);
        assertThat(saved.getCreatedDate()).isNotNull();
        verify(mapper).insertRole(saved);
    }

    @Test
    void createRole_rejectsDuplicateCode() {
        asAdmin("admin");
        when(mapper.findRoleByCode("TEST_ROLE")).thenReturn(new WebRole());
        assertThatThrownBy(() -> service.createRole(validRole(), null, "admin"))
                .isInstanceOf(ExternalGroupService.DuplicateRoleCodeException.class);
        verify(mapper, never()).insertRole(any());
    }

    @Test
    void createRole_validatesRequiredFields() {
        asAdmin("admin");
        WebRole noCode = new WebRole();
        noCode.setRoleName("Name");
        assertThatThrownBy(() -> service.createRole(noCode, null, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("code");
        WebRole noName = new WebRole();
        noName.setRoleCode("CODE");
        assertThatThrownBy(() -> service.createRole(noName, null, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void createRole_validatesColumnLengths() {
        asAdmin("admin");
        WebRole role = new WebRole();
        role.setRoleCode("C");
        role.setRoleName("N".repeat(WebRole.ROLE_NAME_MAX_LENGTH + 1));
        assertThatThrownBy(() -> service.createRole(role, null, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not exceed");
    }

    @Test
    void findUsers_returnsRequestedPage() {
        when(mapper.countUsers(null)).thenReturn(25L);
        when(mapper.findUsersPage(0L, 10, null)).thenReturn(List.of(new ExternalUser()));

        PageResult<ExternalUser> result = service.findUsers(1, 10, null);

        assertThat(result.getRows()).hasSize(1);
        assertThat(result.getTotalRows()).isEqualTo(25L);
        assertThat(result.getPageNumber()).isEqualTo(1);
        assertThat(result.getLastPage()).isEqualTo(3);
    }

    @Test
    void findUsers_clampsPageBeyondTheEnd() {
        when(mapper.countUsers("john")).thenReturn(25L);
        when(mapper.findUsersPage(20L, 10, "john")).thenReturn(List.of(new ExternalUser()));

        PageResult<ExternalUser> result = service.findUsers(99, 10, "  john  ");

        verify(mapper).countUsers("john");
        assertThat(result.getPageNumber()).isEqualTo(3);
    }

    @Test
    void findUsers_emptyResult() {
        when(mapper.countUsers(null)).thenReturn(0L);

        PageResult<ExternalUser> result = service.findUsers(1, 10, "");

        assertThat(result.getRows()).isEmpty();
        assertThat(result.getTotalRows()).isZero();
        verify(mapper, never()).findUsersPage(anyLong(), anyInt(), any());
    }

    private ExternalUser user(long id, String username) {
        ExternalUser user = new ExternalUser();
        user.setId(String.valueOf(id));
        user.setUsername(username);
        return user;
    }

    @Test
    void addMembership_insertsEnabledMembership() {
        asAdmin("admin");
        WebRole role = new WebRole();
        role.setRoleId(5L);
        role.setRoleCode("GRP");
        when(mapper.findRoleById(5L)).thenReturn(role);
        when(mapper.findUserByIdAndUserName(123L,"rradwan")).thenReturn(user(123, "john.doe"));
        when(mapper.countMembership(5L, 123L)).thenReturn(0);
        when(mapper.findNumericUserId("admin")).thenReturn(7L);

        GroupMembership saved = service.addMembership(5L, 123L, null, "admin","rradwam");

        assertThat(saved.getRoleId()).isEqualTo(5L);
        assertThat(saved.getUserId()).isEqualTo(123L);
        assertThat(saved.getUserWebName()).isEqualTo("john.doe");
        assertThat(saved.getIsEnabled()).isEqualTo(1);
        assertThat(saved.getCreatedBy()).isEqualTo(7L);
        verify(mapper).insertMembership(saved);
    }

    @Test
    void addMembership_rejectsDuplicate() {
        asAdmin("admin");
        when(mapper.findRoleById(5L)).thenReturn(new WebRole());
        when(mapper.findUserByIdAndUserName(123L,"rradwan")).thenReturn(user(123, "john.doe"));
        when(mapper.countMembership(5L, 123L)).thenReturn(1);

        assertThatThrownBy(() -> service.addMembership(5L, 123L, null, "admin","rradwam"))
                .isInstanceOf(ExternalGroupService.MembershipExistsException.class)
                .hasMessageContaining("already a member");
        verify(mapper, never()).insertMembership(any());
    }

    @Test
    void addMembership_rejectsMissingGroupOrUser() {
        asAdmin("admin");
        when(mapper.findRoleById(5L)).thenReturn(null);
        assertThatThrownBy(() -> service.addMembership(5L, 123L, null, "admin","rradwam"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("group");
        when(mapper.findRoleById(5L)).thenReturn(new WebRole());
        when(mapper.findUserByIdAndUserName(123L,"rradwan")).thenReturn(null);
        assertThatThrownBy(() -> service.addMembership(5L, 123L, null, "admin","rradwam"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("user");
    }

    @Test
    void addMembership_rejectsNonAdmin() {
        when(identityService.createGroupQuery()).thenReturn(groupQuery); when(groupQuery.groupMember("attacker")).thenReturn(groupQuery); when(groupQuery.groupId(ExternalGroupService.ADMIN_ROLE_CODE)).thenReturn(groupQuery); when(groupQuery.count()).thenReturn(0L);
        assertThatThrownBy(() -> service.addMembership(5L, 123L, null, "attacker","rradwam"))
                .isInstanceOf(SecurityException.class);
        verify(mapper, never()).insertMembership(any());
    }

    @Test
    void findMembershipsOfRole_emptyForNullRole() {
        assertThat(service.findMembershipsOfRole(null)).isEmpty();
        verify(mapper, never()).findMembershipsByRole(anyLong());
    }
}
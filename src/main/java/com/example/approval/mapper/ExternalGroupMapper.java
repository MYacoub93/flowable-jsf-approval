package com.example.approval.mapper;

import com.example.approval.entity.ExternalUser;
import com.example.approval.entity.GroupMembership;
import com.example.approval.entity.WebRole;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * MyBatis mapper (external Oracle datasource) for the external group manager:
 * {@code WEB_ROLES}, {@code WEB_USER_ROLES} and {@code FLOWABLE_USERS_VW}.
 *
 * <p>SQL lives in {@code resources/mapper/ExternalGroupMapper.xml}; every
 * statement is parameterised (#{...}) - user input is never concatenated.</p>
 */
public interface ExternalGroupMapper {

    // ------------------------------------------------------------------
    // WEB_ROLES
    // ------------------------------------------------------------------

    /** All roles, ordered by secondary name then code (dropdown + list). */
    List<WebRole> findAllRoles();

    /** Roles matching an (optional, case-insensitive) code or name filter, ordered. */
    List<WebRole> findRoles(@Param("roleCode") String roleCode,
                            @Param("roleName") String roleName);

    /** One role by PK. */
    WebRole findRoleById(@Param("roleId") Long roleId);

    /** Case-insensitive exact role_code lookup (duplicate check). */
    WebRole findRoleByCode(@Param("roleCode") String roleCode);

    /** NEXT role_id = MAX(role_id)+1 (no sequence/trigger on this schema). */
    Long nextRoleId();

    /** Insert a new WEB_ROLES row. */
    int insertRole(WebRole role);

    // ------------------------------------------------------------------
    // FLOWABLE_USERS_VW (paginated)
    // ------------------------------------------------------------------

    /** One page of users (server-side OFFSET/FETCH), ordered by USERNAME_. */
    List<ExternalUser> findUsersPage(@Param("offset") long offset,
                                     @Param("pageSize") int pageSize,
                                     @Param("searchTerm") String searchTerm);

    /** Total users matching the same filter as {@link #findUsersPage}. */
    long countUsers(@Param("searchTerm") String searchTerm);

    /** One user of the view by numeric id (validation before insert). */
    ExternalUser findUserByIdAndUserName(@Param("userId") long userId,@Param("userName") String userName);

    /**
     * Numeric business user id (DIC_USERS.USER_ID / view ID_) behind a
     * Flowable username - used to fill the NOT NULL CREATED_BY columns.
     * Same convention as BpmAuditMapper.findNumericUserId.
     */
    Long findNumericUserId(@Param("username") String username);

    // ------------------------------------------------------------------
    // WEB_USER_ROLES
    // ------------------------------------------------------------------

    /** Membership count for (role_id, user_id) - duplicate check. */
    int countMembership(@Param("roleId") Long roleId,
                        @Param("userId") Long userId);

    /** Insert a membership row (PK role_id+user_id enforced by Oracle). */
    int insertMembership(GroupMembership membership);

    /** Members of one role, with display names resolved from the view. */
    List<GroupMembership> findMembershipsByRole(@Param("roleId") Long roleId);

    /** All memberships of one user (for "already member" hints). */
    List<GroupMembership> findMembershipsByUser(@Param("userId") Long userId);
}
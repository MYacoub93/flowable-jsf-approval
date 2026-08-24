package com.example.approval.service;

import com.example.approval.entity.ExternalUser;
import com.example.approval.entity.GroupMembership;
import com.example.approval.entity.PageResult;
import com.example.approval.entity.WebRole;
import com.example.approval.mapper.ExternalGroupMapper;
import org.flowable.engine.IdentityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service layer for the external group manager (Oracle {@code WEB_ROLES} /
 * {@code WEB_USER_ROLES} + {@code FLOWABLE_USERS_VW}).
 *
 * <p>Layering follows the existing conventions:
 * JSF backing bean -> this service -> {@link ExternalGroupMapper}
 * (MyBatis, {@code externalSqlSessionFactory}).</p>
 *
 * <p>All write operations run on the Oracle-scoped
 * {@code externalTransactionManager}; ORA-00001 from the composite PK
 * {@code WEB_USER_ROLES_PK (USER_ID, ROLE_ID)} is translated into a clean
 * "already a member" error in addition to the pre-check.</p>
 */
@Service
public class ExternalGroupService {

    private static final Logger log = LoggerFactory.getLogger(ExternalGroupService.class);

    /** Fallback for the NOT NULL CREATED_BY columns when no numeric id is known. */
    private static final int UNKNOWN_USER_ID = 0;

    /** Default number of user rows per page (server-side pagination). */
    public static final int DEFAULT_PAGE_SIZE = 10;

    /**
     * Only members of this external role (Flowable group id = ROLE_CODE in
     * WEB_ROLES) may create groups and manage memberships. Same mechanism as
     * {@code ClearanceService.isMemberOfGroup}: a Flowable IdentityService
     * group query resolved live against the Oracle views - memberships take
     * effect immediately, no restart needed.
     */
    public static final String ADMIN_ROLE_CODE = "ADM";

    private final ExternalGroupMapper mapper;
    private final IdentityService identityService;

    public ExternalGroupService(ExternalGroupMapper mapper, IdentityService identityService) {
        this.mapper = mapper;
        this.identityService = identityService;
    }

    /**
     * Whether the (logged-in) Flowable user may manage external groups.
     *
     * @param flowableUserId the Flowable user id (loginBean.getCurrentUser().getId())
     */
    public boolean isGroupAdmin(String flowableUserId) {
        if (flowableUserId == null || flowableUserId.isBlank()) {
            return false;
        }
        return identityService.createGroupQuery()
                .groupMember(flowableUserId)
                .groupId(ADMIN_ROLE_CODE)
                .count() > 0;
    }

    private void assertGroupAdmin(String flowableUserId) {
        if (!isGroupAdmin(flowableUserId)) {
            throw new SecurityException("User " + flowableUserId
                    + " is not allowed to manage external groups (requires group "
                    + ADMIN_ROLE_CODE + ")");
        }
    }

    // ------------------------------------------------------------------
    // Roles / groups (WEB_ROLES)
    // ------------------------------------------------------------------

    /** All groups for the dropdown, ordered by name. */
    public List<WebRole> findAllRoles() {
        return mapper.findAllRoles();
    }

    /**
     * Create a new external group after validation.
     *
     * @return the persisted role (with generated roleId)
     * @throws IllegalArgumentException on validation errors (field, message)
     * @throws DuplicateRoleCodeException when role_code already exists
     */
    @Transactional(transactionManager = "externalTransactionManager")
    public WebRole createRole(WebRole role, Long actorUserId, String actorFlowableUserId) {
        assertGroupAdmin(actorFlowableUserId);
        validateRole(role);

        if (mapper.findRoleByCode(role.getRoleCode()) != null) {
            throw new DuplicateRoleCodeException(role.getRoleCode());
        }

        role.setRoleId(mapper.nextRoleId());
        role.setIsFixed(0);
        role.setKeyType(WebRole.KEY_TYPE_GENERIC);
        Long createdBy = resolveActorUserId(actorUserId, actorFlowableUserId);
        role.setCreatedBy(createdBy != null ? createdBy : UNKNOWN_USER_ID);
        role.setCreatedDate(LocalDateTime.now());
        try {
            mapper.insertRole(role);
        } catch (DataIntegrityViolationException e) {
            // Concurrent insert of the same ROLE_ID PK or duplicate-ish data
            log.warn("Integrity violation creating role {}: {}",
                    role.getRoleCode(), e.getMostSpecificCause().getMessage());
            throw new DuplicateRoleCodeException(role.getRoleCode());
        }
        log.info("Created external role {} ({}) with ROLE_ID {}",
                role.getRoleCode(), role.getRoleName(), role.getRoleId());
        return role;
    }

    private void validateRole(WebRole role) {
        if (role == null) {
            throw new IllegalArgumentException("Role is required");
        }
        // Trim whitespace on all free-text fields first
        if (role.getRoleCode() != null) {
            role.setRoleCode(role.getRoleCode().trim());
        }
        if (role.getRoleName() != null) {
            role.setRoleName(role.getRoleName().trim());
        }
        if (role.getRoleNameS() != null) {
            role.setRoleNameS(role.getRoleNameS().trim());
        }
        if (role.getHomeMsg() != null) {
            role.setHomeMsg(role.getHomeMsg().trim());
        }
        if (role.getHomeMsgS() != null) {
            role.setHomeMsgS(role.getHomeMsgS().trim());
        }

        if (isBlank(role.getRoleCode())) {
            throw new IllegalArgumentException("Role code is required");
        }
        if (role.getRoleCode().length() > WebRole.ROLE_CODE_MAX_LENGTH) {
            throw new IllegalArgumentException("Role code must not exceed "
                    + WebRole.ROLE_CODE_MAX_LENGTH + " characters");
        }
        if (isBlank(role.getRoleName())) {
            throw new IllegalArgumentException("Role name is required");
        }
        if (role.getRoleName().length() > WebRole.ROLE_NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("Role name must not exceed "
                    + WebRole.ROLE_NAME_MAX_LENGTH + " characters");
        }
        if (role.getRoleNameS() != null && role.getRoleNameS().length() > WebRole.ROLE_NAME_S_MAX_LENGTH) {
            throw new IllegalArgumentException("Secondary role name must not exceed "
                    + WebRole.ROLE_NAME_S_MAX_LENGTH + " characters");
        }
        if (role.getHomeMsg() != null && role.getHomeMsg().length() > WebRole.HOME_MSG_MAX_LENGTH) {
            throw new IllegalArgumentException("Home message must not exceed "
                    + WebRole.HOME_MSG_MAX_LENGTH + " characters");
        }
        if (role.getHomeMsgS() != null && role.getHomeMsgS().length() > WebRole.HOME_MSG_MAX_LENGTH) {
            throw new IllegalArgumentException("Secondary home message must not exceed "
                    + WebRole.HOME_MSG_MAX_LENGTH + " characters");
        }
    }

    // ------------------------------------------------------------------
    // Users (FLOWABLE_USERS_VW, server-side pagination)
    // ------------------------------------------------------------------

    /**
     * One page of external users, filtered by an optional search term
     * (matches username / name / email / id) - only {@code pageSize} rows
     * are ever fetched from Oracle.
     */
    @Transactional(transactionManager = "externalTransactionManager", readOnly = true)
    public PageResult<ExternalUser> findUsers(int pageNumber, int pageSize, String searchTerm) {
        int page = Math.max(pageNumber, 1);
        int size = pageSize <= 0 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, 100);
        String term = normalizeSearch(searchTerm);
        long total = mapper.countUsers(term);
        if (total == 0) {
            return PageResult.empty(page, size);
        }
        long offset = (long) (page - 1) * size;
        if (offset >= total) {
            // requested page beyond the end - clamp to last page
            page = (int) ((total + size - 1) / size);
            offset = (long) (page - 1) * size;
        }
        List<ExternalUser> rows = mapper.findUsersPage(offset, size, term);
        return new PageResult<>(rows, total, page, size);
    }

    private String normalizeSearch(String searchTerm) {
        if (searchTerm == null) {
            return null;
        }
        String t = searchTerm.trim();
        return t.isEmpty() ? null : t;
    }

    // ------------------------------------------------------------------
    // Memberships (WEB_USER_ROLES)
    // ------------------------------------------------------------------

    /** Members of a group (with user-web-name and enabled flag). */
    @Transactional(transactionManager = "externalTransactionManager", readOnly = true)
    public List<GroupMembership> findMembershipsOfRole(Long roleId) {
        if (roleId == null) {
            return List.of();
        }
        return mapper.findMembershipsByRole(roleId);
    }

    /**
     * Add a user to a group. Validates that both the group and the user exist
     * and rejects duplicates before inserting; the composite PK
     * (USER_ID, ROLE_ID) is the final guard and its violation is translated
     * into the same "already a member" error.
     *
     * @throws MembershipExistsException when the user is already in the group
     * @throws IllegalArgumentException   for missing group/user
     */
    @Transactional(transactionManager = "externalTransactionManager")
    public GroupMembership addMembership(Long roleId, Long userId, Long actorUserId,
                                         String actorFlowableUserId) {
        assertGroupAdmin(actorFlowableUserId);
        if (roleId == null) {
            throw new IllegalArgumentException("A group must be selected");
        }
        if (userId == null) {
            throw new IllegalArgumentException("A user must be selected");
        }

        WebRole role = mapper.findRoleById(roleId);
        if (role == null) {
            throw new IllegalArgumentException("Selected group does not exist (id " + roleId + ")");
        }

        ExternalUser user = mapper.findUserById(userId);
        if (user == null) {
            throw new IllegalArgumentException("Selected user does not exist (id " + userId + ")");
        }
        String webName = user.getUsername() == null ? String.valueOf(userId) : user.getUsername();
        if (webName.length() > GroupMembership.USER_WEB_NAME_MAX_LENGTH) {
            // USER_WEB_NAME is VARCHAR2(20); usernames are always <= 20 today,
            // but keep the insert safe if the view ever yields longer values.
            webName = webName.substring(0, GroupMembership.USER_WEB_NAME_MAX_LENGTH);
        }

        if (mapper.countMembership(roleId, userId) > 0) {
            throw new MembershipExistsException(user.getUsername());
        }

        GroupMembership membership = new GroupMembership();
        membership.setRoleId(roleId);
        membership.setUserId(userId);
        membership.setUserWebName(webName);
        membership.setIsEnabled(1);
        Long createdBy = resolveActorUserId(actorUserId, actorFlowableUserId);
        membership.setCreatedBy(createdBy != null ? createdBy : UNKNOWN_USER_ID);
        membership.setCreatedDate(LocalDateTime.now());
        try {
            mapper.insertMembership(membership);
        } catch (DataIntegrityViolationException e) {
            // ORA-00001 on WEB_USER_ROLES_PK - concurrent duplicate insert
            throw new MembershipExistsException(user.getUsername());
        }
        log.info("Added user {} ({}) to role {} ({})", webName, userId, role.getRoleCode(), roleId);
        return membership;
    }

    // ------------------------------------------------------------------
    // Exceptions
    // ------------------------------------------------------------------

    /** role_code already present in WEB_ROLES. */
    public static class DuplicateRoleCodeException extends RuntimeException {
        public DuplicateRoleCodeException(String roleCode) {
            super("A group with code '" + roleCode + "' already exists");
        }
    }

    /** (role_id, user_id) already present in WEB_USER_ROLES. */
    public static class MembershipExistsException extends RuntimeException {
        public MembershipExistsException(String username) {
            super("User '" + username + "' is already a member of this group");
        }
    }

    /**
     * Numeric DIC_USERS id behind the acting user, for the NOT NULL
     * CREATED_BY columns: supplied id first, then a username lookup in the
     * external view (same convention as
     * {@code BpmAuditMapper.findNumericUserId}), else null (= 0 fallback).
     */
    private Long resolveActorUserId(Long actorUserId, String flowableUserId) {
        if (actorUserId != null) {
            return actorUserId;
        }
        if (flowableUserId == null || flowableUserId.isBlank()) {
            return null;
        }
        try {
            return mapper.findNumericUserId(flowableUserId);
        } catch (Exception e) {
            log.warn("Could not resolve numeric user id of '{}'", flowableUserId, e);
            return null;
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}

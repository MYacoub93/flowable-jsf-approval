package com.example.approval.service;


import com.example.approval.entity.AuthenticatedUser;
import com.example.approval.mapper.FlowableIdentityMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.flowable.idm.engine.impl.persistence.entity.GroupEntityImpl;
import org.flowable.idm.engine.impl.persistence.entity.UserEntityImpl;

import java.util.List;

/**
 * Service layer for User operations.
 * Used by JSF beans and by ApprovalService for assignee resolution.
 */
@Service
@Transactional(readOnly = true)
public class FlowableIdentityService {

    @Autowired
    private FlowableIdentityMapper identityMapper;

    /**
     * The login user lookup against FLOWABLE_USERS_VW. Returns
     * {@link AuthenticatedUser}, which carries the identity columns
     * (id, first/last name, e-mail, password for the login comparison)
     * <b>plus</b> the default role and role code selected from the same row
     * (DEFAULT_ROLE_ / ROLE_CODE_). The role values may be null - they are
     * informational only and never influence the authentication result.
     */
    public AuthenticatedUser findUserByUsernameForAuth(String username) {
        return identityMapper.findUserByUsernameForAuth(username);
    }

    /**
     * Exact (case-insensitive) user lookup by login username - used by the
     * admin "Users Management" page. Reuses the existing
     * {@link FlowableIdentityMapper#findUserByUsername} query against
     * FLOWABLE_USERS_VW; returns null when no such user exists.
     */
    public UserEntityImpl findUserByUsername(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        return identityMapper.findUserByUsername(username.trim());
    }

    /**
     * Group memberships (id, name, type) of one user, resolved live from
     * flowable_groups_vw - the same view that backs Flowable's own group
     * queries (e.g. ExternalGroupService.isGroupAdmin). {@code userId} is
     * the numeric user id (FLOWABLE_USERS_VW.ID_) as a string.
     */
    public List<GroupEntityImpl> findGroupsOfUser(String userId) {
        if (userId == null || userId.isBlank()) {
            return List.of();
        }
        return identityMapper.findGroupsByUser(userId.trim());
    }

}
package com.example.approval.service;


import com.example.approval.mapper.FlowableIdentityMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.flowable.idm.api.User;
import org.flowable.idm.engine.impl.persistence.entity.GroupEntityImpl;
import org.flowable.idm.engine.impl.persistence.entity.UserEntityImpl;

import java.util.List;
import java.util.Optional;

/**
 * Service layer for User operations.
 * Used by JSF beans and by ApprovalService for assignee resolution.
 */
@Service
@Transactional(readOnly = true)
public class FlowableIdentityService {

    @Autowired
    private FlowableIdentityMapper identityMapper;

    public User findUserByUsernameForAuth(String username) {


        org.flowable.idm.api.User flowableUser = identityMapper.findUserByUsernameForAuth(username);

        return flowableUser;
        //return Optional.ofNullable(flowableUser);
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
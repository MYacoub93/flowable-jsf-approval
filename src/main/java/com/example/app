package com.example.approval.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.flowable.idm.engine.impl.persistence.entity.GroupEntityImpl;
import org.flowable.idm.engine.impl.persistence.entity.UserEntityImpl;
import java.util.List;

@Mapper
public interface FlowableIdentityMapper {
    UserEntityImpl findUserByUsernameForAuth(String username);
    UserEntityImpl findUserById(String id);
    UserEntityImpl findUserByUsername(String username);
    List<GroupEntityImpl> findGroupsByUser(String userId);

    /** Groups with the exact id, sourced from the SIS view (usually 0..1 rows). */
    List<GroupEntityImpl> findGroupById(String groupId);

    /**
     * E-mail addresses of every member of a group (FLOWABLE_USERS_VW rows whose
     * ROLE_CODE_ equals the group id). Used by the notification subsystem to
     * mail all approvers of a candidate group; empty when the group has no
     * members with an e-mail.
     */
    List<String> findEmailsByGroup(String groupId);

    /**
     * E-mail address of a single user from FLOWABLE_USERS_VW (used when a task
     * is claimed by one person, or to notify the initiator of the result).
     * Returns {@code null} when the user or e-mail is unknown.
     */
    String findEmailByUsername(String username);
}
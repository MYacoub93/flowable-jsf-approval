package com.example.approval.mapper;

import com.example.approval.entity.AuthenticatedUser;
import com.example.approval.entity.ExternalUser;
import org.apache.ibatis.annotations.Mapper;
import org.flowable.idm.engine.impl.persistence.entity.GroupEntityImpl;
import org.flowable.idm.engine.impl.persistence.entity.UserEntityImpl;

import java.util.List;

@Mapper
public interface FlowableIdentityMapper {

    /**
     * The login lookup against FLOWABLE_USERS_VW (password included for the
     * comparison in UserLoginBean). Returns {@link AuthenticatedUser} because
     * the query additionally selects USERNAME_, DEFAULT_ROLE_ (default role)
     * and ROLE_CODE_ (role code) - properties Flowable's own
     * {@code UserEntityImpl} does not have. Both role values may be
     * {@code null}; they are informational only and never affect the
     * authentication result.
     */
    AuthenticatedUser findUserByUsernameForAuth(String username);

    UserEntityImpl findUserById(String id);

    UserEntityImpl findUserByUsername(String username);

    List<GroupEntityImpl> findGroupsByUser(String userId);

    /** Groups with the exact id, sourced from the SIS view (usually 0..1 rows). */
    List<GroupEntityImpl> findGroupById(String groupId);

    /**
     * Username and e-mail address of every member of a group
     * (FLOWABLE_USERS_VW rows whose ROLE_CODE_ equals the group id). Used by
     * the notification subsystem to mail <b>every member individually</b> of a
     * candidate group - a group never resolves to a shared group mailbox.
     * Empty when the group is unknown or has no members.
     */
    List<ExternalUser> findMembersByGroup(String groupId);

    /**
     * E-mail address of a single user from FLOWABLE_USERS_VW (used when a task
     * is claimed by one person, or to notify the initiator of the result).
     * Returns {@code null} when the user or e-mail is unknown.
     */
    String findEmailByUsername(String username);
}
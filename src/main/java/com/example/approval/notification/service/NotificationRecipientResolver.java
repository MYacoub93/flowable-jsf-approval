package com.example.approval.notification.service;

import com.example.approval.mapper.FlowableIdentityMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * Resolves notification recipient addresses from the SIS view
 * {@code FLOWABLE_USERS_VW} (external Oracle datasource).
 *
 * <p>Two dedicated queries back this resolver:</p>
 * <ul>
 *   <li>{@code findEmailsByGroup} - all e-mail addresses of the members of a
 *       candidate group ({@code ROLE_CODE_} = group id). Used when a task is
 *       offered to a whole group: <b>every member is mailed</b>;</li>
 *   <li>{@code findEmailByUsername} - the single address of one user. Used when
 *       a task was <i>claimed</i> (has an assignee) or when the initiator is
 *       notified of the final result.</li>
 * </ul>
 *
 * <p><b>Failure tolerance:</b> notifications must never break the Flowable
 * transaction they run inside, so every database error is caught, logged and
 * degrades to "no addresses found" - callers then fall back to the static
 * {@code notification.*} mailbox configuration or skip the mail.</p>
 */
@Component
public class NotificationRecipientResolver {

    private static final Logger log = LoggerFactory.getLogger(NotificationRecipientResolver.class);

    private final FlowableIdentityMapper identityMapper;

    public NotificationRecipientResolver(FlowableIdentityMapper identityMapper) {
        this.identityMapper = identityMapper;
    }

    /**
     * All distinct e-mail addresses of the given group's members, pulled from
     * {@code FLOWABLE_USERS_VW}. Empty (never {@code null}) when the group is
     * unknown, has no members with an address, or the query fails.
     */
    public List<String> resolveGroupEmails(String groupId) {
        if (groupId == null || groupId.isBlank()) {
            return List.of();
        }
        try {
            List<String> emails = identityMapper.findEmailsByGroup(groupId.trim());
            if (emails == null) {
                return List.of();
            }
            return emails.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(e -> !e.isEmpty())
                    .distinct()
                    .toList();
        } catch (Exception e) {
            log.warn("Could not load member e-mails of group '{}' from FLOWABLE_USERS_VW: {}",
                    groupId, e.getMessage());
            return List.of();
        }
    }

    /**
     * The e-mail address of a single user from {@code FLOWABLE_USERS_VW}, or
     * {@code null} when the user / address is unknown or the query fails.
     */
    public String resolveUserEmail(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        try {
            String email = identityMapper.findEmailByUsername(username.trim());
            return (email != null && !email.isBlank()) ? email.trim() : null;
        } catch (Exception e) {
            log.warn("Could not load e-mail of user '{}' from FLOWABLE_USERS_VW: {}",
                    username, e.getMessage());
            return null;
        }
    }
}
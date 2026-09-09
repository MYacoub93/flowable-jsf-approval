package com.example.approval.notification.service;

import com.example.approval.entity.ExternalUser;
import com.example.approval.mapper.FlowableIdentityMapper;
import com.example.approval.notification.model.GroupEmailResolution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Resolves notification recipients from the SIS view
 * {@code FLOWABLE_USERS_VW} (external Oracle datasource) - the same source of
 * truth the whole application uses for users and groups.
 *
 * <p>Group notifications follow the <b>Group → Users → Emails → Recipients</b>
 * resolution:</p>
 * <ul>
 *   <li>{@code findMembersByGroup} - the <b>members</b> (username + e-mail) of
 *       a candidate group ({@code ROLE_CODE_} = group id). Used when a task is
 *       offered to a whole group: <b>every member is mailed individually</b>.
 *       A group <b>never</b> resolves to a shared/group mailbox;</li>
 *   <li>{@code findEmailByUsername} - the single address of one user. Used when
 *       a task was <i>claimed</i> (has an assignee) or when the initiator is
 *       notified of the final result.</li>
 * </ul>
 *
 * <p><b>Recipient hygiene:</b> members without an e-mail address, blank
 * addresses, invalid addresses and duplicate addresses are removed before the
 * list is handed to the mail sender (one send call with all recipients).</p>
 *
 * <p><b>Failure tolerance:</b> notifications must never break the Flowable
 * transaction they run inside, so every database error is caught, logged and
 * degrades to "no addresses found" - callers then skip the mail.</p>
 */
@Component
public class NotificationRecipientResolver {

    private static final Logger log = LoggerFactory.getLogger(NotificationRecipientResolver.class);

    /**
     * Conservative single-recipient address sanity check: {@code local@domain}
     * with a dotted domain of at least two labels. Deliberately permissive -
     * the SMTP server remains the final authority.
     */
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^\\s@,;:<>\\[\\]\"']{1,64}@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?"
                    + "(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)+$");

    private final FlowableIdentityMapper identityMapper;

    public NotificationRecipientResolver(FlowableIdentityMapper identityMapper) {
        this.identityMapper = identityMapper;
    }

    /**
     * Resolves a notification target <b>group</b> to the validated, deduplicated
     * e-mail addresses of <b>all its members</b> (Group → Users → Emails).
     *
     * <p>Members without an address, blank/invalid addresses and duplicates are
     * removed; the resolution counts are logged for audit purposes:
     * {@code Notification target: GROUP / Group: X / Resolved users: N /
     * Valid email recipients: M / Skipped users: K}.</p>
     *
     * @return the resolution; never {@code null}, but possibly without
     *         recipients when the group is unknown, empty, has no members with
     *         a valid address, or the lookup fails.
     */
    public GroupEmailResolution resolveGroupRecipients(String groupId) {
        if (groupId == null || groupId.isBlank()) {
            return new GroupEmailResolution(groupId, List.of(), 0, 0, false);
        }
        List<ExternalUser> members;
        try {
            members = identityMapper.findMembersByGroup(groupId.trim());
        } catch (Exception e) {
            log.warn("Group membership lookup failed for group '{}' (FLOWABLE_USERS_VW): {}",
                    groupId, e.getMessage());
            logGroupResolution(groupId, 0, 0, 0);
            return new GroupEmailResolution(groupId, List.of(), 0, 0, true);
        }
        if (members == null || members.isEmpty()) {
            // unknown group and empty group are indistinguishable in the view -
            // both mean "nobody to notify" and must not break the workflow
            log.info("Group '{}' has no members in FLOWABLE_USERS_VW - no notification recipients",
                    groupId);
            logGroupResolution(groupId, 0, 0, 0);
            return new GroupEmailResolution(groupId, List.of(), 0, 0, false);
        }

        // deduplicate members by username first (a user may carry several roles)
        Set<String> seenUsernames = new LinkedHashSet<>();
        List<ExternalUser> distinctMembers = new ArrayList<>(members.size());
        for (ExternalUser member : members) {
            if (member == null) {
                continue;
            }
            String username = member.getUsername() == null ? "" : member.getUsername().trim();
            if (username.isEmpty() || !seenUsernames.add(username.toLowerCase())) {
                continue;
            }
            distinctMembers.add(member);
        }

        // then collect validated, deduplicated e-mail addresses
        Set<String> recipients = new LinkedHashSet<>();
        int skipped = 0;
        for (ExternalUser member : distinctMembers) {
            String email = member.getEmail() == null ? "" : member.getEmail().trim();
            if (email.isEmpty()) {
                log.debug("Member '{}' of group '{}' has no e-mail address - skipped",
                        member.getUsername(), groupId);
                skipped++;
            } else if (!EMAIL_PATTERN.matcher(email).matches()) {
                log.warn("Member '{}' of group '{}' has an invalid e-mail address - skipped",
                        member.getUsername(), groupId);
                skipped++;
            } else if (!recipients.add(email.toLowerCase())) {
                // same address already collected from another member
                log.debug("Duplicate e-mail address among members of group '{}' - kept once",
                        groupId);
            }
        }

        List<String> recipientList = new ArrayList<>(recipients);
        logGroupResolution(groupId, distinctMembers.size(), recipientList.size(), skipped);
        return new GroupEmailResolution(groupId, recipientList,
                distinctMembers.size(), skipped, false);
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

    private void logGroupResolution(String groupId, int resolvedUsers,
                                    int validRecipients, int skippedUsers) {
        log.info("Notification target: GROUP | Group: {} | Resolved users: {} | "
                        + "Valid email recipients: {} | Skipped users: {}",
                groupId, resolvedUsers, validRecipients, skippedUsers);
    }
}
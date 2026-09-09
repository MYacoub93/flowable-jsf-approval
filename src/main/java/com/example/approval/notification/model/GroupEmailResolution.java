package com.example.approval.notification.model;

import java.util.List;

/**
 * Result of resolving a notification target <b>group</b> to its individual
 * member e-mail addresses (Group → Users → Emails → Recipients).
 *
 * <p>Carries the audit counts logged by
 * {@code NotificationRecipientResolver}:</p>
 * <pre>
 * Notification target: GROUP
 * Group: <group-id>
 * Resolved users: <count>
 * Valid email recipients: <count>
 * Skipped users: <count>
 * </pre>
 *
 * <p>A group <b>never</b> resolves to a shared/group mailbox - only to the
 * personal addresses of its members. Instances are immutable.</p>
 */
public final class GroupEmailResolution {

    private final String groupId;

    /** Deduplicated, validated e-mail addresses of the group members. */
    private final List<String> recipients;

    /** Number of distinct members returned by the group membership lookup. */
    private final int resolvedUsers;

    /** Members skipped because they had no (valid) e-mail address. */
    private final int skippedUsers;

    /** Whether the membership lookup itself failed (datasource outage). */
    private final boolean lookupFailed;

    public GroupEmailResolution(String groupId, List<String> recipients,
                                int resolvedUsers, int skippedUsers, boolean lookupFailed) {
        this.groupId = groupId;
        this.recipients = recipients == null ? List.of() : List.copyOf(recipients);
        this.resolvedUsers = resolvedUsers;
        this.skippedUsers = skippedUsers;
        this.lookupFailed = lookupFailed;
    }

    public String getGroupId() {
        return groupId;
    }

    public List<String> getRecipients() {
        return recipients;
    }

    public int getResolvedUsers() {
        return resolvedUsers;
    }

    public int getSkippedUsers() {
        return skippedUsers;
    }

    public boolean isLookupFailed() {
        return lookupFailed;
    }

    /** Whether at least one valid recipient address was resolved. */
    public boolean hasRecipients() {
        return !recipients.isEmpty();
    }
}
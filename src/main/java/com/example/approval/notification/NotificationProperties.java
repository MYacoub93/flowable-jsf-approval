package com.example.approval.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Global configuration of the notification subsystem
 * (prefix {@code notification.*} in application.yml) - usable by every
 * Flowable process, not just one specific workflow.
 *
 * <pre>
 * notification:
 *   from: noreply@example.edu
 *   task-link-base: http://localhost:8080
 *   always-log: true
 *   user-email-domain: students.example.edu
 *   group-mailboxes:            # Flowable candidate group / department id -> mailbox
 *     IT Department: it@example.edu
 *   user-mailboxes:             # username -> mailbox (overrides the domain convention)
 *     student.john: john@example.edu
 *   task-link-paths:            # process definition key -> JSF task page path
 *     clearanceLetterProcess: /clearance-task.xhtml
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "notification")
public class NotificationProperties {

    /**
     * Global on/off switch. When {@code false} nothing is sent (and nothing
     * logged beyond a single info line) - the workflow keeps running.
     */
    private boolean enabled = true;

    /** Sender address used for every notification. */
    private String from = "noreply@example.edu";

    /**
     * Public base URL of the portal, used to build task deep links such as
     * {@code http://host:8080/clearance-task.xhtml?taskId=...}.
     */
    private String taskLinkBase = "http://localhost:8080";

    /**
     * When true (default) every notification is additionally written to the
     * application log, so the flow is demonstrable without an SMTP server.
     */
    private boolean alwaysLog = true;

    /**
     * Mail domain appended to plain usernames when no explicit user mailbox
     * is configured: {@code student.john -> student.john@students.example.edu}.
     */
    private String userEmailDomain = "students.example.edu";

    /**
     * Group mailbox per Flowable candidate group / department id. Keys
     * containing spaces must be quoted in YAML:
     * {@code "[IT Department]": it@example.edu}. Multiple recipients can be
     * given comma-separated.
     */
    private Map<String, String> groupMailboxes = new LinkedHashMap<>();

    /**
     * Explicit mailbox per username; wins over the {@link #userEmailDomain}
     * convention.
     */
    private Map<String, String> userMailboxes = new LinkedHashMap<>();

    /**
     * Per-process JSF task page path used to build deep links:
     * {@code task-link-base + path + "?taskId=" + taskId}. Processes without
     * an entry fall back to {@link #defaultTaskLinkPath}.
     */
    private Map<String, String> taskLinkPaths = new LinkedHashMap<>();

    /** Fallback path when the process key has no entry in {@link #taskLinkPaths}. */
    private String defaultTaskLinkPath = "/task.xhtml";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTaskLinkBase() {
        return taskLinkBase;
    }

    public void setTaskLinkBase(String taskLinkBase) {
        this.taskLinkBase = taskLinkBase;
    }

    public boolean isAlwaysLog() {
        return alwaysLog;
    }

    public void setAlwaysLog(boolean alwaysLog) {
        this.alwaysLog = alwaysLog;
    }

    public String getUserEmailDomain() {
        return userEmailDomain;
    }

    public void setUserEmailDomain(String userEmailDomain) {
        this.userEmailDomain = userEmailDomain;
    }

    public Map<String, String> getGroupMailboxes() {
        return groupMailboxes;
    }

    public void setGroupMailboxes(Map<String, String> groupMailboxes) {
        this.groupMailboxes = groupMailboxes;
    }

    public Map<String, String> getUserMailboxes() {
        return userMailboxes;
    }

    public void setUserMailboxes(Map<String, String> userMailboxes) {
        this.userMailboxes = userMailboxes;
    }

    public Map<String, String> getTaskLinkPaths() {
        return taskLinkPaths;
    }

    public void setTaskLinkPaths(Map<String, String> taskLinkPaths) {
        this.taskLinkPaths = taskLinkPaths;
    }

    public String getDefaultTaskLinkPath() {
        return defaultTaskLinkPath;
    }

    public void setDefaultTaskLinkPath(String defaultTaskLinkPath) {
        this.defaultTaskLinkPath = defaultTaskLinkPath;
    }
}
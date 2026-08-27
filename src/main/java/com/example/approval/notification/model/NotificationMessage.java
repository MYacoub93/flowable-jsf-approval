package com.example.approval.notification.model;

/**
 * Process-agnostic notification message handed to
 * {@code NotificationService#send(NotificationMessage)}.
 *
 * <p>Any Flowable process (Clearance Letter, future approval processes, ...)
 * describes <b>what</b> happened and <b>who</b> should be notified; the shared
 * {@code EmailNotificationService} decides <b>how</b> the recipient address is
 * resolved and how the e-mail body is rendered.</p>
 *
 * <p>Recipient resolution order (first match wins):</p>
 * <ol>
 *   <li>{@link #getRecipientEmail()} - explicit address;</li>
 *   <li>{@code notification.group-mailboxes} keyed by {@link #getDepartment()};</li>
 *   <li>{@code notification.group-mailboxes} keyed by {@link #getCandidateGroup()};</li>
 *   <li>{@code notification.user-mailboxes} keyed by {@link #getRecipientUser()};</li>
 *   <li>convention {@code recipientUser@user-email-domain}.</li>
 * </ol>
 *
 * <p>Instances are immutable - create them with {@link #builder()}.</p>
 */
public final class NotificationMessage {

    /** Coarse notification category; only affects default subject/body rendering. */
    public enum Type {

        /** A task is waiting for an approver (group or user). */
        TASK_ASSIGNED,

        /** Final outcome notification to the initiator. */
        RESULT,

        /** Anything else - caller fully controls the texts. */
        CUSTOM
    }

    private final Type type;

    private final String processKey;
    private final String processName;
    private final String processInstanceId;

    private final String stage;
    private final String department;
    private final String candidateGroup;

    private final String recipientUser;
    private final String recipientEmail;

    private final String taskId;
    private final String taskLinkPath;

    private final String initiator;

    private final String subject;
    private final String intro;
    private final String additionalInfo;

    private NotificationMessage(Builder builder) {
        this.type = builder.type;
        this.processKey = builder.processKey;
        this.processName = builder.processName;
        this.processInstanceId = builder.processInstanceId;
        this.stage = builder.stage;
        this.department = builder.department;
        this.candidateGroup = builder.candidateGroup;
        this.recipientUser = builder.recipientUser;
        this.recipientEmail = builder.recipientEmail;
        this.taskId = builder.taskId;
        this.taskLinkPath = builder.taskLinkPath;
        this.initiator = builder.initiator;
        this.subject = builder.subject;
        this.intro = builder.intro;
        this.additionalInfo = builder.additionalInfo;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Type getType() {
        return type;
    }

    /** Flowable process definition key, e.g. {@code clearanceLetterProcess}. */
    public String getProcessKey() {
        return processKey;
    }

    /** Human readable process name, e.g. "Clearance Letter". */
    public String getProcessName() {
        return processName;
    }

    /** Process instance id (used in links / audit references). */
    public String getProcessInstanceId() {
        return processInstanceId;
    }

    /** Stage code of the current step, e.g. {@code DEPARTMENT_APPROVAL}. May be null. */
    public String getStage() {
        return stage;
    }

    /** Department / approver group display name. May equal the candidate group. */
    public String getDepartment() {
        return department;
    }

    /** Flowable candidate group id receiving the task. May be null. */
    public String getCandidateGroup() {
        return candidateGroup;
    }

    /** Username of a single recipient (typically the initiator). May be null. */
    public String getRecipientUser() {
        return recipientUser;
    }

    /** Explicit recipient address; wins over every other resolution. May be null. */
    public String getRecipientEmail() {
        return recipientEmail;
    }

    /** Flowable task id for the deep link. May be null. */
    public String getTaskId() {
        return taskId;
    }

    /** Optional task page path override, e.g. {@code /clearance-task.xhtml}. */
    public String getTaskLinkPath() {
        return taskLinkPath;
    }

    /** Original initiator username. May be null. */
    public String getInitiator() {
        return initiator;
    }

    /** Ready-made subject; when null a default is derived from type/process. */
    public String getSubject() {
        return subject;
    }

    /** Main body sentence(s), e.g. "A clearance approval task is waiting for you." */
    public String getIntro() {
        return intro;
    }

    /** Optional free text appended after the structured fields. */
    public String getAdditionalInfo() {
        return additionalInfo;
    }

    /**
     * Fluent builder:
     * {@code NotificationMessage.builder().type(...).processKey(...).build()}.
     */
    public static final class Builder {

        private Type type = Type.CUSTOM;
        private String processKey;
        private String processName;
        private String processInstanceId;
        private String stage;
        private String department;
        private String candidateGroup;
        private String recipientUser;
        private String recipientEmail;
        private String taskId;
        private String taskLinkPath;
        private String initiator;
        private String subject;
        private String intro;
        private String additionalInfo;

        private Builder() {
        }

        public Builder type(Type type) {
            this.type = type;
            return this;
        }

        public Builder processKey(String processKey) {
            this.processKey = processKey;
            return this;
        }

        public Builder processName(String processName) {
            this.processName = processName;
            return this;
        }

        public Builder processInstanceId(String processInstanceId) {
            this.processInstanceId = processInstanceId;
            return this;
        }

        public Builder stage(String stage) {
            this.stage = stage;
            return this;
        }

        public Builder department(String department) {
            this.department = department;
            return this;
        }

        public Builder candidateGroup(String candidateGroup) {
            this.candidateGroup = candidateGroup;
            return this;
        }

        public Builder recipientUser(String recipientUser) {
            this.recipientUser = recipientUser;
            return this;
        }

        public Builder recipientEmail(String recipientEmail) {
            this.recipientEmail = recipientEmail;
            return this;
        }

        public Builder taskId(String taskId) {
            this.taskId = taskId;
            return this;
        }

        public Builder taskLinkPath(String taskLinkPath) {
            this.taskLinkPath = taskLinkPath;
            return this;
        }

        public Builder initiator(String initiator) {
            this.initiator = initiator;
            return this;
        }

        public Builder subject(String subject) {
            this.subject = subject;
            return this;
        }

        public Builder intro(String intro) {
            this.intro = intro;
            return this;
        }

        public Builder additionalInfo(String additionalInfo) {
            this.additionalInfo = additionalInfo;
            return this;
        }

        public NotificationMessage build() {
            return new NotificationMessage(this);
        }
    }
}
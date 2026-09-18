package com.example.approval.notification.model;

import java.util.List;
import java.util.Objects;

/**
 * Immutable, self-contained SMTP payload handed across the async boundary.
 *
 * <p><b>Transaction/thread contract:</b> everything the async worker thread
 * needs is materialized BEFORE dispatch - recipients (already resolved from
 * FLOWABLE_USERS_VW), subject and body (already rendered). No Flowable
 * entities, no MyBatis lazy objects, no request/session-scoped state, no
 * FacesContext, no Flowable authenticated-user context are reachable from
 * this object. The async thread only performs the SMTP call.</p>
 *
 * <p>Identifiers (process instance id, task id, notification type) are
 * carried along purely for <b>logging</b> so failures can be traced back to
 * the exact workflow node - they are never dereferenced.</p>
 */
public final class EmailDispatchRequest {

    private final String processInstanceId;
    private final String taskId;
    private final String notificationType;
    private final List<String> recipients;
    private final String subject;
    private final String body;

    private EmailDispatchRequest(Builder builder) {
        this.processInstanceId = builder.processInstanceId;
        this.taskId = builder.taskId;
        this.notificationType = builder.notificationType;
        this.recipients = List.copyOf(builder.recipients);
        this.subject = builder.subject;
        this.body = builder.body;
        if (recipients.isEmpty()) {
            throw new IllegalArgumentException("recipients must not be empty");
        }
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("subject must not be blank");
        }
        if (body == null) {
            throw new IllegalArgumentException("body must not be null");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getProcessInstanceId() {
        return processInstanceId;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getNotificationType() {
        return notificationType;
    }

    /** Unmodifiable, already deduplicated/validated recipient list. */
    public List<String> getRecipients() {
        return recipients;
    }

    public String getSubject() {
        return subject;
    }

    public String getBody() {
        return body;
    }

    public int getRecipientCount() {
        return recipients.size();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        EmailDispatchRequest that = (EmailDispatchRequest) o;
        return Objects.equals(processInstanceId, that.processInstanceId)
                && Objects.equals(taskId, that.taskId)
                && Objects.equals(notificationType, that.notificationType)
                && Objects.equals(recipients, that.recipients)
                && Objects.equals(subject, that.subject)
                && Objects.equals(body, that.body);
    }

    @Override
    public int hashCode() {
        return Objects.hash(processInstanceId, taskId, notificationType,
                recipients, subject, body);
    }

    @Override
    public String toString() {
        return "EmailDispatchRequest{processInstanceId='" + processInstanceId
                + "', taskId='" + taskId + "', notificationType='" + notificationType
                + "', recipientCount=" + recipients.size()
                + ", subject='" + subject + "'}";
    }

    public static final class Builder {
        private String processInstanceId;
        private String taskId;
        private String notificationType;
        private List<String> recipients = List.of();
        private String subject;
        private String body;

        private Builder() {
        }

        public Builder processInstanceId(String processInstanceId) {
            this.processInstanceId = processInstanceId;
            return this;
        }

        public Builder taskId(String taskId) {
            this.taskId = taskId;
            return this;
        }

        public Builder notificationType(String notificationType) {
            this.notificationType = notificationType;
            return this;
        }

        public Builder recipients(List<String> recipients) {
            this.recipients = (recipients == null) ? List.of() : recipients;
            return this;
        }

        public Builder subject(String subject) {
            this.subject = subject;
            return this;
        }

        public Builder body(String body) {
            this.body = body;
            return this;
        }

        public EmailDispatchRequest build() {
            return new EmailDispatchRequest(this);
        }
    }
}
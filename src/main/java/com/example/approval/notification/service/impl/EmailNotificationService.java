package com.example.approval.notification.service.impl;

import com.example.approval.notification.NotificationProperties;
import com.example.approval.notification.model.NotificationMessage;
import com.example.approval.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * SMTP-backed implementation of the global {@link NotificationService}.
 *
 * <p>Not tied to any specific process: recipients are resolved from the
 * shared {@code notification.*} configuration - explicit
 * {@code recipientEmail}, then {@code notification.group-mailboxes} (by
 * department / candidate group), then {@code notification.user-mailboxes} or
 * the {@code notification.user-email-domain} convention. Task deep links are
 * built from {@code notification.task-link-base} plus the per-process
 * {@code notification.task-link-paths} mapping. Groups without a configured
 * mailbox are only logged (never fatal). When no mail server is configured at
 * all, notifications degrade to log output.</p>
 *
 * <p><b>Failure tolerance:</b> e-mail is infrastructure, not workflow. Every
 * send failure is caught and logged so the Flowable transaction (task
 * creation, completion) is never rolled back because of a mail outage.</p>
 */
@Service
public class EmailNotificationService implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);

    private final NotificationProperties properties;

    private final JavaMailSender mailSender;

    public EmailNotificationService(NotificationProperties properties,
                                    ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.properties = properties;
        this.mailSender = mailSenderProvider.getIfAvailable();
    }

    @Override
    public void send(NotificationMessage message) {
        if (message == null) {
            return;
        }
        if (!properties.isEnabled()) {
            log.debug("Notifications disabled - skipping '{}'", subjectOf(message));
            return;
        }

        String to = resolveRecipient(message);
        String subject = subjectOf(message);
        String body = renderBody(message);

        sendMail(to, subject, body);
    }

    // ------------------------------------------------------------------
    // rendering
    // ------------------------------------------------------------------

    private String subjectOf(NotificationMessage message) {
        if (message.getSubject() != null && !message.getSubject().isBlank()) {
            return message.getSubject();
        }
        String processName = safe(message.getProcessName());
        if (message.getType() == NotificationMessage.Type.TASK_ASSIGNED) {
            return "[" + processName + "] Approval required by "
                    + safe(message.getDepartment() != null
                            ? message.getDepartment()
                            : message.getCandidateGroup());
        }
        if (message.getType() == NotificationMessage.Type.RESULT) {
            return "[" + processName + "] Process result";
        }
        return "[" + processName + "] Notification";
    }

    private String renderBody(NotificationMessage message) {
        StringBuilder body = new StringBuilder();

        String recipientName = message.getDepartment() != null
                ? message.getDepartment()
                : message.getRecipientUser();
        String greeting = recipientName != null ? "Dear " + recipientName : "Dear user";
        body.append(greeting).append(",\n\n");

        if (message.getIntro() != null && !message.getIntro().isBlank()) {
            body.append(message.getIntro().trim()).append("\n\n");
        }

        if (message.getProcessName() != null) {
            body.append("Process            : ").append(message.getProcessName()).append("\n");
        }
        if (message.getProcessInstanceId() != null) {
            body.append("Process instance   : ").append(message.getProcessInstanceId()).append("\n");
        }
        if (message.getStage() != null) {
            body.append("Stage              : ").append(message.getStage()).append("\n");
        }
        if (message.getDepartment() != null) {
            body.append("Department         : ").append(message.getDepartment()).append("\n");
        }
        if (message.getInitiator() != null) {
            body.append("Initiator          : ").append(message.getInitiator()).append("\n");
        }
        if (message.getAdditionalInfo() != null && !message.getAdditionalInfo().isBlank()) {
            body.append("Additional info    : ").append(message.getAdditionalInfo()).append("\n");
        }
        if (message.getTaskId() != null) {
            body.append("Task reference     : ").append(message.getTaskId()).append("\n");
            String link = buildTaskLink(message);
            if (link != null) {
                body.append("Open task          : ").append(link).append("\n");
            }
        }
        body.append("\nThis is an automated notification. Please do not reply.\n");
        return body.toString();
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private void sendMail(String to, String subject, String body) {
        if (to == null || to.isBlank()) {
            log.warn("No recipient configured - notification skipped. Subject: '{}'", subject);
            return;
        }
        if (properties.isAlwaysLog()) {
            log.info("[NOTIFICATION] to='{}' subject='{}'\n{}", to, subject, body);
        }
        if (mailSender != null) {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(properties.getFrom());
                message.setTo(to.split("\\s*,\\s*"));
                message.setSubject(subject);
                message.setText(body);
                mailSender.send(message);
            } catch (Exception e) {
                log.error("Failed to send notification '{}' to '{}': {}",
                        subject, to, e.getMessage(), e);
            }
        } else {
            log.info("No JavaMailSender configured - notification only logged (subject '{}')", subject);
        }
    }

    /**
     * Resolution order: explicit address, group mailbox by department, group
     * mailbox by candidate group, user mailbox, username@domain convention.
     */
    private String resolveRecipient(NotificationMessage message) {
        if (message.getRecipientEmail() != null && !message.getRecipientEmail().isBlank()) {
            return message.getRecipientEmail();
        }
        Map<String, String> groupMailboxes = properties.getGroupMailboxes();
        if (message.getDepartment() != null && groupMailboxes.containsKey(message.getDepartment())) {
            return groupMailboxes.get(message.getDepartment());
        }
        if (message.getCandidateGroup() != null
                && groupMailboxes.containsKey(message.getCandidateGroup())) {
            return groupMailboxes.get(message.getCandidateGroup());
        }
        String user = message.getRecipientUser();
        if (user != null) {
            Map<String, String> userMailboxes = properties.getUserMailboxes();
            if (userMailboxes.containsKey(user)) {
                return userMailboxes.get(user);
            }
            if (properties.getUserEmailDomain() != null
                    && !properties.getUserEmailDomain().isBlank()) {
                return user + "@" + properties.getUserEmailDomain();
            }
        }
        return null;
    }

    private String buildTaskLink(NotificationMessage message) {
        if (message.getTaskId() == null) {
            return null;
        }
        String path = message.getTaskLinkPath();
        if (path == null || path.isBlank()) {
            path = properties.getTaskLinkPaths()
                    .getOrDefault(message.getProcessKey(), properties.getDefaultTaskLinkPath());
        }
        if (path == null || path.isBlank()) {
            return null;
        }
        String base = properties.getTaskLinkBase();
        return (base == null ? "http://localhost:8080" : base) + path
                + "?taskId=" + safe(message.getTaskId());
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
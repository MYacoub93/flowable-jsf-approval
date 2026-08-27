package com.example.approval.notification.service.impl;

import com.example.approval.notification.NotificationProperties;
import com.example.approval.notification.model.NotificationMessage;
import com.example.approval.notification.service.NotificationRecipientResolver;
import com.example.approval.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * SMTP-backed implementation of the global {@link NotificationService}.
 *
 * <p><b>Recipient resolution is database-first</b> - addresses are pulled from
 * the SIS view {@code FLOWABLE_USERS_VW} via
 * {@link NotificationRecipientResolver}:</p>
 * <ol>
 *   <li>explicit {@code recipientEmail} on the message (always wins);</li>
 *   <li><b>claimed task</b> - when the message carries an
 *       {@code assigneeUser}, only that person's address is used;</li>
 *   <li><b>group task</b> - the addresses of <b>every member</b> of the
 *       candidate group ({@code ROLE_CODE_} = group id) are collected and all
 *       of them receive the mail;</li>
 *   <li>static fallbacks from {@code notification.*}: {@code user-mailboxes},
 *       {@code user-email-domain} convention, then
 *       {@code group-mailboxes}.</li>
 * </ol>
 *
 * <p>Task deep links are built from {@code notification.task-link-base} plus
 * the per-process {@code notification.task-link-paths} mapping. When no mail
 * server is configured at all, notifications degrade to log output.</p>
 *
 * <p><b>Failure tolerance:</b> e-mail is infrastructure, not workflow. Every
 * send / lookup failure is caught and logged so the Flowable transaction (task
 * creation, completion) is never rolled back because of a mail or SIS
 * outage.</p>
 */
@Service
public class EmailNotificationService implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);

    private final NotificationProperties properties;

    private final NotificationRecipientResolver recipientResolver;

    private final JavaMailSender mailSender;

    public EmailNotificationService(NotificationProperties properties,
                                    NotificationRecipientResolver recipientResolver,
                                    ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.properties = properties;
        this.recipientResolver = recipientResolver;
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

        List<String> recipients = resolveRecipients(message);
        String subject = subjectOf(message);
        String body = renderBody(message);

        sendMail(recipients, subject, body);
    }

    // ------------------------------------------------------------------
    // recipient resolution (FLOWABLE_USERS_VW first, config fallback)
    // ------------------------------------------------------------------

    /**
     * Resolution order:
     * <ol>
     *   <li>explicit {@code recipientEmail} on the message;</li>
     *   <li>{@code assigneeUser} (claimed task) - exactly that person, address
     *       from FLOWABLE_USERS_VW;</li>
     *   <li>candidate group - <b>all member addresses</b> from
     *       FLOWABLE_USERS_VW ({@code ROLE_CODE_} = group id);</li>
     *   <li>static config fallbacks ({@code user-mailboxes} /
     *       {@code user-email-domain} / {@code group-mailboxes}).</li>
     * </ol>
     */
    private List<String> resolveRecipients(NotificationMessage message) {
        List<String> recipients = new ArrayList<>();

        // 1. explicit address always wins
        if (message.getRecipientEmail() != null && !message.getRecipientEmail().isBlank()) {
            recipients.add(message.getRecipientEmail().trim());
            return recipients;
        }

        // 2. claimed task -> notify only the assignee
        String assignee = message.getAssigneeUser();
        if (assignee != null && !assignee.isBlank()) {
            String email = recipientResolver.resolveUserEmail(assignee);
            if (email != null) {
                recipients.add(email);
                return recipients;
            }
            // config fallbacks for the single user
            String fallback = fallbackForUser(assignee);
            if (fallback != null) {
                recipients.add(fallback);
                return recipients;
            }
            log.warn("No e-mail found in FLOWABLE_USERS_VW for assignee '{}' - notification skipped",
                    assignee);
            return recipients;
        }

        // 3. group task -> mail every member of the candidate group
        String group = message.getCandidateGroup() != null
                ? message.getCandidateGroup()
                : message.getDepartment();
        if (group != null && !group.isBlank()) {
            List<String> groupEmails = recipientResolver.resolveGroupEmails(group);
            if (!groupEmails.isEmpty()) {
                recipients.addAll(groupEmails);
                return recipients;
            }
            // static group mailbox fallback (e.g. shared department inbox)
            Map<String, String> groupMailboxes = properties.getGroupMailboxes();
            String mailbox = groupMailboxes.get(group);
            if (mailbox != null && !mailbox.isBlank()) {
                recipients.add(mailbox.trim());
                return recipients;
            }
            log.warn("No member e-mails found in FLOWABLE_USERS_VW for group '{}' - notification skipped",
                    group);
            return recipients;
        }

        // 4. plain user recipient (e.g. initiator result notification)
        String user = message.getRecipientUser();
        if (user != null && !user.isBlank()) {
            String email = recipientResolver.resolveUserEmail(user);
            if (email == null) {
                email = fallbackForUser(user);
            }
            if (email != null) {
                recipients.add(email);
            } else {
                log.warn("No e-mail found in FLOWABLE_USERS_VW for user '{}' - notification skipped",
                        user);
            }
            return recipients;
        }

        return recipients;
    }

    /** {@code user-mailboxes} entry, then the {@code username@domain} convention. */
    private String fallbackForUser(String user) {
        Map<String, String> userMailboxes = properties.getUserMailboxes();
        if (userMailboxes.containsKey(user)) {
            return userMailboxes.get(user);
        }
        if (properties.getUserEmailDomain() != null
                && !properties.getUserEmailDomain().isBlank()) {
            return user + "@" + properties.getUserEmailDomain();
        }
        return null;
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

    private void sendMail(List<String> recipients, String subject, String body) {
        if (recipients == null || recipients.isEmpty()) {
            log.warn("No recipient resolved - notification skipped. Subject: '{}'", subject);
            return;
        }
        String[] to = recipients.toArray(new String[0]);
        if (properties.isAlwaysLog()) {
            log.info("[NOTIFICATION] to='{}' subject='{}'\n{}", String.join(", ", recipients), subject, body);
        }
        if (mailSender != null) {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(properties.getFrom());
                message.setTo(to);
                message.setSubject(subject);
                message.setText(body);
                mailSender.send(message);
            } catch (Exception e) {
                log.error("Failed to send notification '{}' to '{}': {}",
                        subject, recipients, e.getMessage(), e);
            }
        } else {
            log.info("No JavaMailSender configured - notification only logged (subject '{}')", subject);
        }
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
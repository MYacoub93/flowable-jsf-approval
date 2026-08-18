package com.example.approval.clearance.service.impl;

import com.example.approval.clearance.ClearanceConstants;
import com.example.approval.clearance.ClearanceProperties;
import com.example.approval.clearance.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * SMTP-backed implementation of {@link NotificationService}.
 *
 * <p>Sends via {@code spring-boot-starter-mail} ({@link JavaMailSender}) and
 * resolves recipient addresses per candidate group from
 * {@code clearance.notification.group-mailboxes}. Group ids that have no
 * configured mailbox are only logged (never fatal). When no mail server is
 * configured at all, notifications degrade to log output.</p>
 *
 * <p><b>Failure tolerance:</b> e-mail is infrastructure, not workflow. Every
 * send failure is caught and logged so the Flowable transaction (task
 * creation, completion) is never rolled back because of a mail outage.</p>
 */
@Service
public class EmailNotificationService implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);

    private final ClearanceProperties properties;

    private final JavaMailSender mailSender;

    public EmailNotificationService(ClearanceProperties properties,
                                    ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.properties = properties;
        this.mailSender = mailSenderProvider.getIfAvailable();
    }

    @Override
    public void sendTaskNotification(String processInstanceId,
                                     String processName,
                                     String stage,
                                     String department,
                                     String candidateGroup,
                                     String taskId,
                                     String initiator,
                                     String subject,
                                     String additionalInfo) {
        String to = resolveRecipient(department, candidateGroup);
        String link = buildTaskLink(taskId);

        String body = "Dear " + safe(department) + " team,\n\n"
                + "A clearance approval task is waiting for your department.\n\n"
                + "Process            : " + safe(processName) + "\n"
                + "Process instance   : " + safe(processInstanceId) + "\n"
                + "Stage              : " + safe(stage) + "\n"
                + "Department         : " + safe(department) + "\n"
                + "Initiator          : " + safe(initiator) + "\n"
                + (additionalInfo != null && !additionalInfo.isBlank()
                        ? "Additional info    : " + additionalInfo + "\n"
                        : "")
                + "Task reference     : " + safe(taskId) + "\n"
                + "Open task          : " + link + "\n\n"
                + "This is an automated notification. Please do not reply.\n";

        sendMail(to, subject, body);
    }

    @Override
    public void sendResultNotification(String processInstanceId, String initiator, String resultText) {
        String to = resolveUserMailbox(initiator);
        String body = "Dear " + safe(initiator) + ",\n\n"
                + safe(resultText) + "\n\n"
                + "Process            : " + ClearanceConstants.PROCESS_NAME + "\n"
                + "Process instance   : " + safe(processInstanceId) + "\n"
                + "The full audit trail is available in the portal.\n\n"
                + "This is an automated notification. Please do not reply.\n";

        sendMail(to, "[" + ClearanceConstants.PROCESS_NAME + "] " + resultText, body);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private void sendMail(String to, String subject, String body) {
        if (to == null || to.isBlank()) {
            log.warn("No recipient configured - notification skipped. Subject: '{}'", subject);
            return;
        }
        if (properties.getNotification().isAlwaysLog()) {
            log.info("[CLEARANCE-NOTIFICATION] to='{}' subject='{}'\n{}", to, subject, body);
        }
        if (mailSender != null) {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(properties.getNotification().getFrom());
                message.setTo(to.split("\\s*,\\s*"));
                message.setSubject(subject);
                message.setText(body);
                mailSender.send(message);
            } catch (Exception e) {
                log.error("Failed to send clearance notification '{}' to '{}': {}",
                        subject, to, e.getMessage(), e);
            }
        } else {
            log.info("No JavaMailSender configured - notification only logged (subject '{}')", subject);
        }
    }

    private String resolveRecipient(String department, String candidateGroup) {
        Map<String, String> mailboxes = properties.getNotification().getGroupMailboxes();
        if (department != null && mailboxes.containsKey(department)) {
            return mailboxes.get(department);
        }
        if (candidateGroup != null && mailboxes.containsKey(candidateGroup)) {
            return mailboxes.get(candidateGroup);
        }
        return null;
    }

    private String resolveUserMailbox(String username) {
        // Convention: username@students.example.edu unless configured otherwise.
        return username == null ? null : username + "@students.example.edu";
    }

    private String buildTaskLink(String taskId) {
        String base = properties.getNotification().getTaskLinkBase();
        return (base == null ? "http://localhost:8080" : base)
                + "/clearance-task.xhtml?taskId=" + safe(taskId);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
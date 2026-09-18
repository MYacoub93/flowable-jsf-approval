package com.example.approval.notification.service;

import com.example.approval.notification.NotificationProperties;
import com.example.approval.notification.config.EmailNotificationAsyncConfig;
import com.example.approval.notification.model.EmailDispatchRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * The CENTRAL asynchronous boundary between the workflow and SMTP.
 *
 * <p>Every workflow e-mail funnels through
 * {@link #dispatch(EmailDispatchRequest)}, which is annotated
 * {@code @Async("emailNotificationExecutor")} - Spring executes the SMTP
 * call on the dedicated bounded thread pool
 * ({@link EmailNotificationAsyncConfig}) instead of the calling Flowable /
 * JSF request thread. The caller returns as soon as the e-mail is
 * scheduled.</p>
 *
 * <p><b>Why a separate bean:</b> {@code @Async} works through a Spring
 * proxy; calling an {@code @Async} method from another method of the SAME
 * bean bypasses the proxy and would silently run SMTP on the caller
 * thread. Keeping the boundary in its own bean and having
 * {@code EmailNotificationService} call it cross-bean guarantees the
 * proxy - and therefore the executor - is always used. Callers never need
 * to know about asynchrony; any current or future user of
 * {@code NotificationService} gets it automatically.</p>
 *
 * <p><b>Statelessness / thread safety:</b> this bean holds only immutable
 * configuration ({@code from} address) and the shared {@link JavaMailSender}
 * (thread-safe by contract). Per-invocation data lives exclusively in the
 * immutable {@link EmailDispatchRequest}; there are no mutable singleton
 * fields for recipients, subject, body or current task.</p>
 *
 * <p><b>Error isolation:</b> this method NEVER lets an exception escape.
 * SMTP failures are logged (with process instance id, task id, notification
 * type, recipient count and subject for traceability) and swallowed - the
 * Flowable transaction that scheduled the mail has long since committed and
 * must never be affected by e-mail infrastructure problems. No passwords,
 * credentials or tokens are ever logged; message bodies are only logged
 * when the explicit {@code notification.always-log} development switch is
 * on.</p>
 */
@Component
public class AsyncEmailDispatcher {

    private static final Logger log = LoggerFactory.getLogger(AsyncEmailDispatcher.class);

    private final NotificationProperties properties;

    private final JavaMailSender mailSender;

    public AsyncEmailDispatcher(NotificationProperties properties,
                                ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.properties = properties;
        this.mailSender = mailSenderProvider.getIfAvailable();
    }

    /**
     * Sends one fully prepared notification on the e-mail executor thread.
     *
     * <p>Must only be called with a self-contained
     * {@link EmailDispatchRequest}: recipients resolved and body/subject
     * rendered BEFORE crossing this boundary, so the worker thread needs no
     * Flowable/SIS transaction, no FacesContext and no request/session
     * state.</p>
     *
     * <p>Void on purpose: a {@code @Async} void method's exceptions are
     * handled here (caught + logged), never propagated to the caller.</p>
     */
    @Async(EmailNotificationAsyncConfig.EXECUTOR_BEAN_NAME)
    public void dispatch(EmailDispatchRequest request) {
        if (request == null) {
            log.warn("Async e-mail dispatch called with null request - ignored");
            return;
        }
        // Optional development logging of the full notification content.
        if (properties.isAlwaysLog()) {
            log.info("[NOTIFICATION] to='{}' subject='{}'\n{}",
                    String.join(", ", request.getRecipients()),
                    request.getSubject(), request.getBody());
        }
        if (mailSender == null) {
            // No SMTP configured (e.g. local dev) - degrade to log output.
            log.info("No JavaMailSender configured - notification only logged "
                    + "(type '{}', {} recipient(s), subject '{}')",
                    request.getNotificationType(), request.getRecipientCount(),
                    request.getSubject());
            return;
        }
        log.debug("E-mail sending started (type '{}', {} recipient(s), subject '{}')",
                request.getNotificationType(), request.getRecipientCount(), request.getSubject());
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(properties.getFrom());
            message.setTo(request.getRecipients().toArray(new String[0]));
            message.setSubject(request.getSubject());
            message.setText(request.getBody());
            mailSender.send(message);
            log.info("E-mail sent successfully (processInstanceId='{}', taskId='{}', "
                            + "type '{}', {} recipient(s), subject '{}')",
                    request.getProcessInstanceId(), request.getTaskId(),
                    request.getNotificationType(), request.getRecipientCount(),
                    request.getSubject());
        } catch (Exception e) {
            // Infrastructure failure: log with identifiers and DO NOT
            // rethrow - the workflow transaction must stay unaffected.
            log.error("E-mail sending failed (processInstanceId='{}', taskId='{}', "
                            + "type '{}', {} recipient(s), subject '{}'): {}",
                    request.getProcessInstanceId(), request.getTaskId(),
                    request.getNotificationType(), request.getRecipientCount(),
                    request.getSubject(), e.getMessage(), e);
        }
    }
}
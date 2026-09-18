package com.example.approval.notification;

import com.example.approval.notification.model.EmailDispatchRequest;
import com.example.approval.notification.service.AsyncEmailDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AsyncEmailDispatcher} - the SMTP-side worker behind
 * the {@code @Async} boundary.
 *
 * <p>{@code dispatch(...)} is invoked DIRECTLY here (on the test thread),
 * which is exactly what the {@code email-N} executor thread does in
 * production. The tests therefore verify the worker-side contract:</p>
 * <ul>
 *   <li>the prepared payload maps 1:1 onto the
 *       {@link SimpleMailMessage} (recipients, subject, body, from);</li>
 *   <li>SMTP failures are caught + logged and NEVER rethrown (the executor
 *       thread must not die or surface the error in the caller);</li>
 *   <li>a missing {@code JavaMailSender} degrades to logging;</li>
 *   <li>concurrent invocations never mix their payloads (thread safety of
 *       the stateless dispatcher + immutable request).</li>
 * </ul>
 *
 * <p>The cross-thread behaviour (which executor runs the method, that the
 * caller is not blocked, that rejection is isolated) is covered by
 * {@code EmailNotificationAsyncIntegrationTest}.</p>
 */
@ExtendWith(MockitoExtension.class)
class AsyncEmailDispatcherTest {

    @Mock
    private ObjectProvider<JavaMailSender> mailSenderProvider;

    @Mock
    private JavaMailSender mailSender;

    private NotificationProperties properties;
    private AsyncEmailDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        properties = new NotificationProperties();
        properties.setFrom("noreply@example.edu");
        properties.setAlwaysLog(false);
    }

    private AsyncEmailDispatcher newDispatcher() {
        // AsyncEmailDispatcher resolves the sender ONCE in its constructor
        // -> the provider must be stubbed before construction (same pattern
        // the production service used before the async refactor).
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
        return new AsyncEmailDispatcher(properties, mailSenderProvider);
    }

    private static EmailDispatchRequest request(
            String pid, String subject, String body, String... recipients) {
        return EmailDispatchRequest.builder()
                .processInstanceId(pid)
                .taskId("task-" + pid)
                .notificationType("TASK_ASSIGNED")
                .recipients(List.of(recipients))
                .subject(subject)
                .body(body)
                .build();
    }

    @Test
    @DisplayName("dispatch maps the payload 1:1 onto the mail message")
    void dispatch_sendsPreparedPayload() {
        dispatcher = newDispatcher();

        dispatcher.dispatch(request("pid-1", "[Clearance] Approval required", "the body",
                "fin.boss@example.edu", "fin.clerk@example.edu"));

        ArgumentCaptor<SimpleMailMessage> captor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertThat(message.getTo()).containsExactly(
                "fin.boss@example.edu", "fin.clerk@example.edu");
        assertThat(message.getSubject()).isEqualTo("[Clearance] Approval required");
        assertThat(message.getText()).isEqualTo("the body");
        assertThat(message.getFrom()).isEqualTo("noreply@example.edu");
    }

    @Test
    @DisplayName("SMTP failure is caught and never rethrown")
    void smtpFailure_isSwallowed() {
        dispatcher = newDispatcher();
        doThrow(new RuntimeException("SMTP down"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        assertDoesNotThrow(() ->
                dispatcher.dispatch(request("pid-1", "[Clearance] subject", "body",
                        "fin.boss@example.edu")));
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("null request is ignored without touching SMTP")
    void nullRequest_isIgnored() {
        dispatcher = newDispatcher();

        assertDoesNotThrow(() -> dispatcher.dispatch(null));
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("no JavaMailSender configured: degrades to logging, no exception")
    void noMailSender_degradesToLogging() {
        when(mailSenderProvider.getIfAvailable()).thenReturn(null);
        dispatcher = new AsyncEmailDispatcher(properties, mailSenderProvider);

        assertDoesNotThrow(() ->
                dispatcher.dispatch(request("pid-1", "[Clearance] subject", "body",
                        "fin.boss@example.edu")));
        // nothing to verify on a null sender - the point is: no exception
    }

    @Test
    @DisplayName("concurrent dispatches never mix recipients/subject/body")
    void concurrentDispatches_keepPayloadsSeparate() throws Exception {
        dispatcher = newDispatcher();
        int notifications = 24;

        // the mock SMTP send records which subject each recipient got and
        // fails none of them; a shared/mutable payload would cross wires
        AtomicInteger mismatches = new AtomicInteger();
        doAnswer(invocation -> {
            SimpleMailMessage message = invocation.getArgument(0);
            String subject = message.getSubject();
            int expectedIndex = Integer.parseInt(subject.substring(subject.indexOf('-') + 1));
            String expectedRecipient = "user" + expectedIndex + "@example.edu";
            String expectedBody = "body-" + expectedIndex;
            if (message.getTo() == null || message.getTo().length != 1
                    || !expectedRecipient.equals(message.getTo()[0])
                    || !("subject-" + expectedIndex).equals(subject)
                    || !expectedBody.equals(message.getText())) {
                mismatches.incrementAndGet();
            }
            return null;
        }).when(mailSender).send(any(SimpleMailMessage.class));

        // barrier: all workers start simultaneously to maximize contention
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(notifications);
        for (int i = 0; i < notifications; i++) {
            final int index = i;
            Thread worker = new Thread(() -> {
                try {
                    start.await();
                    dispatcher.dispatch(request("pid-" + index,
                            "subject-" + index, "body-" + index,
                            "user" + index + "@example.edu"));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
            worker.setDaemon(true);
            worker.start();
        }
        start.countDown();
        // deterministic wait - no Thread.sleep()
        assertThat(done.await(10, TimeUnit.SECONDS))
                .as("all concurrent dispatches must finish").isTrue();

        verify(mailSender, org.mockito.Mockito.times(notifications))
                .send(any(SimpleMailMessage.class));
        assertThat(mismatches.get())
                .as("every recipient must receive exactly his own subject/body")
                .isZero();
    }

    @Test
    @DisplayName("failures and successes coexist: one broken mail stops no other")
    void mixedSuccessAndFailure_isolatePerMail() throws Exception {
        dispatcher = newDispatcher();
        int total = 8;
        CountDownLatch done = new CountDownLatch(total);
        // every odd-indexed mail "fails" on SMTP
        doAnswer(invocation -> {
            SimpleMailMessage message = invocation.getArgument(0);
            done.countDown();
            if (message.getSubject().contains("broken")) {
                throw new RuntimeException("SMTP rejected: " + message.getSubject());
            }
            return null;
        }).when(mailSender).send(any(SimpleMailMessage.class));

        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            for (int i = 0; i < total; i++) {
                final int index = i;
                pool.submit(() -> dispatcher.dispatch(request("pid-" + index,
                        (index % 2 == 0 ? "ok-" : "broken-") + index, "body",
                        "user" + index + "@example.edu")));
            }
            assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
            // every mail was attempted exactly once - failures swallowed
            verify(mailSender, org.mockito.Mockito.times(total))
                    .send(any(SimpleMailMessage.class));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("EmailDispatchRequest: builder enforces non-empty immutable payload")
    void dispatchRequest_isImmutableAndValidated() {
        EmailDispatchRequest request = request("pid-1", "subject", "body", "a@example.edu");
        assertThat(request.getRecipients()).isNotNull();
        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> request.getRecipients().add("b@example.edu"));
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> EmailDispatchRequest.builder()
                        .subject("s").body("b")
                        .recipients(List.of()).build());
    }

    @SuppressWarnings("unused")
    private static Future<?> unused(Future<?> f) {
        return f;
    }
}
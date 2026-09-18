package com.example.approval.notification;

import com.example.approval.entity.ExternalUser;
import com.example.approval.mapper.FlowableIdentityMapper;
import com.example.approval.notification.config.EmailNotificationAsyncConfig;
import com.example.approval.notification.model.NotificationMessage;
import com.example.approval.notification.service.AsyncEmailDispatcher;
import com.example.approval.notification.service.NotificationRecipientResolver;
import com.example.approval.notification.service.impl.EmailNotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.AsyncAnnotationBeanPostProcessor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration tests of the asynchronous e-mail dispatch boundary - the
 * Spring {@code @Async} proxy, the dedicated
 * {@code emailNotificationExecutor} thread pool and the non-blocking
 * contract of {@link EmailNotificationService}.
 *
 * <p>A REAL Spring {@link AsyncAnnotationBeanPostProcessor} proxies the REAL
 * {@link AsyncEmailDispatcher} (the same mechanism {@code @EnableAsync}
 * activates in production), backed by a REAL bounded
 * {@link ThreadPoolTaskExecutor}; only the outermost edges
 * ({@code FLOWABLE_USERS_VW} mapper and {@link JavaMailSender}) are mocks.
 * The {@link EmailNotificationService} is the real production service.</p>
 *
 * <p>All waits use {@link CountDownLatch} / Mockito {@code timeout()} - no
 * {@code Thread.sleep()} anywhere.</p>
 */
@ExtendWith(MockitoExtension.class)
class EmailNotificationAsyncIntegrationTest {

    private static final String INITIATOR = "student.test";

    @Mock
    private FlowableIdentityMapper identityMapper;

    @Mock
    private ObjectProvider<JavaMailSender> mailSenderProvider;

    @Mock
    private JavaMailSender mailSender;

    private ThreadPoolTaskExecutor emailExecutor;
    private EmailNotificationService notificationService;

    @BeforeEach
    void setUp() {
        NotificationProperties properties = new NotificationProperties();
        properties.setEnabled(true);
        properties.setFrom("noreply@example.edu");
        properties.setAlwaysLog(false);
        properties.setUserEmailDomain(null); // DB-only resolution

        // the real dedicated executor, registered under the exact bean name
        // @Async("emailNotificationExecutor") looks up
        emailExecutor = new ThreadPoolTaskExecutor();
        emailExecutor.setCorePoolSize(4);
        emailExecutor.setMaxPoolSize(4);
        emailExecutor.setQueueCapacity(200);
        emailExecutor.setThreadNamePrefix("email-");
        emailExecutor.setWaitForTasksToCompleteOnShutdown(true);
        emailExecutor.setAwaitTerminationSeconds(5);
        emailExecutor.initialize();

        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
        AsyncEmailDispatcher rawDispatcher =
                new AsyncEmailDispatcher(properties, mailSenderProvider);

        // the REAL @Async proxy mechanism (@EnableAsync equivalent)
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton(
                EmailNotificationAsyncConfig.EXECUTOR_BEAN_NAME, emailExecutor);
        AsyncAnnotationBeanPostProcessor bpp = new AsyncAnnotationBeanPostProcessor();
        bpp.setBeanFactory(beanFactory);
        AsyncEmailDispatcher proxiedDispatcher = (AsyncEmailDispatcher) bpp
                .postProcessAfterInitialization(rawDispatcher, "asyncEmailDispatcher");

        notificationService = new EmailNotificationService(properties,
                new NotificationRecipientResolver(identityMapper), proxiedDispatcher);

        // default FLOWABLE_USERS_VW resolution used by all tests: FIN has
        // two members with personal addresses
        when(identityMapper.findMembersByGroup(any())).thenReturn(List.of(
                member("fin.boss", "fin.boss@example.edu"),
                member("fin.clerk", "fin.clerk@example.edu")));
    }

    @AfterEach
    void tearDown() {
        if (emailExecutor != null) {
            emailExecutor.shutdown();
        }
    }

    // ==================================================================
    // SMTP runs on the e-mail executor, not on the caller thread
    // ==================================================================

    @Test
    @DisplayName("send() does NOT execute SMTP synchronously - it runs on an email- worker")
    void send_executesSmtpOnEmailExecutorThread() throws Exception {
        CountDownLatch smtpStarted = new CountDownLatch(1);
        AtomicReference<String> sendingThread = new AtomicReference<>();
        doAnswer(invocation -> {
            sendingThread.set(Thread.currentThread().getName());
            smtpStarted.countDown();
            return null;
        }).when(mailSender).send(any(SimpleMailMessage.class));

        String callerThread = Thread.currentThread().getName();
        notificationService.send(financeTaskMessage());

        assertThat(smtpStarted.await(5, TimeUnit.SECONDS))
                .as("SMTP must execute in the background").isTrue();
        verify(mailSender, timeout(1000)).send(any(SimpleMailMessage.class));
        assertThat(sendingThread.get())
                .as("SMTP must run on the dedicated email- executor thread")
                .startsWith("email-")
                .isNotEqualTo(callerThread);
    }

    @Test
    @DisplayName("send() returns immediately while SMTP is still hanging (caller not blocked)")
    void send_doesNotBlockWhileSmtpHangs() throws Exception {
        CountDownLatch smtpStarted = new CountDownLatch(1);
        CountDownLatch releaseSmtp = new CountDownLatch(1);
        doAnswer(invocation -> {
            smtpStarted.countDown();
            releaseSmtp.await(15, TimeUnit.SECONDS); // SMTP "hangs"
            return null;
        }).when(mailSender).send(any(SimpleMailMessage.class));

        long before = System.nanoTime();
        notificationService.send(financeTaskMessage());
        long elapsedMs = (System.nanoTime() - before) / 1_000_000;

        // the caller returned while SMTP is still blocked -> not blocked
        assertThat(smtpStarted.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(elapsedMs)
                .as("send() must return without waiting for SMTP")
                .isLessThan(3000L);

        releaseSmtp.countDown();
        verify(mailSender, timeout(2000)).send(any(SimpleMailMessage.class));
    }

    // ==================================================================
    // failure isolation
    // ==================================================================

    @Test
    @DisplayName("SMTP failure does not propagate to the caller")
    void smtpFailure_doesNotFailTheCaller() {
        doAnswer(invocation -> {
            throw new RuntimeException("SMTP down");
        }).when(mailSender).send(any(SimpleMailMessage.class));

        assertDoesNotThrow(() -> notificationService.send(financeTaskMessage()));

        // the failing SMTP call really executed in the background
        verify(mailSender, timeout(2000)).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("queue rejection (SMTP outage, bounded queue full) never throws")
    void queueRejection_isIsolatedFromTheCaller() throws Exception {
        // dedicated tiny executor: 1 worker, NO queue - the second mail is
        // rejected while the first one blocks the worker
        ThreadPoolTaskExecutor tinyExecutor = new ThreadPoolTaskExecutor();
        tinyExecutor.setCorePoolSize(1);
        tinyExecutor.setMaxPoolSize(1);
        tinyExecutor.setQueueCapacity(0);
        tinyExecutor.setThreadNamePrefix("email-tiny-");
        tinyExecutor.setRejectedExecutionHandler(
                new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
        tinyExecutor.initialize();

        NotificationProperties properties = new NotificationProperties();
        properties.setEnabled(true);
        properties.setFrom("noreply@example.edu");
        properties.setAlwaysLog(false);
        properties.setUserEmailDomain(null);

        AsyncEmailDispatcher rawDispatcher =
                new AsyncEmailDispatcher(properties, mailSenderProvider);
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton(
                EmailNotificationAsyncConfig.EXECUTOR_BEAN_NAME, tinyExecutor);
        AsyncAnnotationBeanPostProcessor bpp = new AsyncAnnotationBeanPostProcessor();
        bpp.setBeanFactory(beanFactory);
        AsyncEmailDispatcher proxied = (AsyncEmailDispatcher) bpp
                .postProcessAfterInitialization(rawDispatcher, "asyncEmailDispatcher");
        EmailNotificationService tinyService = new EmailNotificationService(properties,
                new NotificationRecipientResolver(identityMapper), proxied);
        CountDownLatch release = new CountDownLatch(1);
        try {
            CountDownLatch firstMailStarted = new CountDownLatch(1);
            doAnswer(invocation -> {
                firstMailStarted.countDown();
                release.await(15, TimeUnit.SECONDS);
                return null;
            }).when(mailSender).send(any(SimpleMailMessage.class));

            assertDoesNotThrow(() -> tinyService.send(financeTaskMessage()));
            assertThat(firstMailStarted.await(5, TimeUnit.SECONDS)).isTrue();

            // worker blocked + no queue -> this submission is REJECTED and
            // must be swallowed + logged by the service, never rethrown
            assertDoesNotThrow(() -> tinyService.send(financeTaskMessage()));

            release.countDown();
            verify(mailSender, timeout(2000)).send(any(SimpleMailMessage.class));
        } finally {
            release.countDown(); // never leave the worker blocked
            tinyExecutor.shutdown();
        }
    }

    // ==================================================================
    // concurrency
    // ==================================================================

    @Test
    @DisplayName("multiple e-mails execute concurrently on the shared pool")
    void multipleEmails_executeConcurrently() throws Exception {
        int concurrent = 4; // == core pool size
        CountDownLatch allInsideSmtp = new CountDownLatch(concurrent);
        CountDownLatch releaseAll = new CountDownLatch(1);
        doAnswer(invocation -> {
            allInsideSmtp.countDown();
            releaseAll.await(15, TimeUnit.SECONDS);
            return null;
        }).when(mailSender).send(any(SimpleMailMessage.class));

        for (int i = 0; i < concurrent; i++) {
            notificationService.send(financeTaskMessage());
        }

        // 4 sends are INSIDE SMTP at the same time -> real concurrency
        assertThat(allInsideSmtp.await(10, TimeUnit.SECONDS))
                .as("all %d mails must be sending concurrently", concurrent).isTrue();

        releaseAll.countDown();
        verify(mailSender, timeout(3000).times(concurrent))
                .send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("recipients/subject/body stay correct when many notifications race")
    void concurrentNotifications_keepPayloadsSeparate() throws Exception {
        int notifications = 12;
        Set<String> seenThreads = ConcurrentHashMap.newKeySet();
        Set<String> subjects = new CopyOnWriteArraySet<>();
        CountDownLatch done = new CountDownLatch(notifications);
        doAnswer(invocation -> {
            SimpleMailMessage message = invocation.getArgument(0);
            seenThreads.add(Thread.currentThread().getName());
            subjects.add(message.getSubject());
            done.countDown();
            return null;
        }).when(mailSender).send(any(SimpleMailMessage.class));

        for (int i = 0; i < notifications; i++) {
            final int index = i;
            notificationService.send(NotificationMessage.builder()
                    .type(NotificationMessage.Type.TASK_ASSIGNED)
                    .processKey("clearanceLetterProcess")
                    .processName("Clearance Letter")
                    .processInstanceId("pid-" + index)
                    .taskId("task-" + index)
                    .department("Finance Department")
                    .candidateGroup("FIN")
                    .initiator(INITIATOR)
                    .subject("subject-" + index)
                    .build());
        }

        assertThat(done.await(10, TimeUnit.SECONDS))
                .as("all notifications must be sent").isTrue();
        assertThat(subjects)
                .as("every mail carries its own subject - no cross-talk")
                .hasSize(notifications);
        assertThat(seenThreads)
                .as("work is distributed over the shared pool")
                .isNotEmpty();
    }

    // ==================================================================
    // payload crosses the boundary intact (subject/body/recipients)
    // ==================================================================

    @Test
    @DisplayName("async e-mail finally delivered with resolved recipients, subject and body")
    void deliveredMail_carriesResolvedPayload() throws Exception {
        CountDownLatch sent = new CountDownLatch(1);
        AtomicReference<SimpleMailMessage> delivered = new AtomicReference<>();
        doAnswer(invocation -> {
            delivered.set(invocation.getArgument(0));
            sent.countDown();
            return null;
        }).when(mailSender).send(any(SimpleMailMessage.class));

        notificationService.send(NotificationMessage.builder()
                .type(NotificationMessage.Type.TASK_ASSIGNED)
                .processKey("clearanceLetterProcess")
                .processName("Clearance Letter")
                .processInstanceId("pid-42")
                .taskId("task-42")
                .stage("FINANCE")
                .department("Finance Department")
                .candidateGroup("FIN")
                .initiator(INITIATOR)
                .subject("[Clearance Letter] Approval required by Finance Department")
                .intro("A new clearance request requires your approval.")
                .build());

        assertThat(sent.await(5, TimeUnit.SECONDS)).isTrue();
        SimpleMailMessage message = delivered.get();
        assertThat(message.getTo()).containsExactlyInAnyOrder(
                "fin.boss@example.edu", "fin.clerk@example.edu");
        assertThat(message.getSubject())
                .isEqualTo("[Clearance Letter] Approval required by Finance Department");
        assertThat(message.getText())
                .contains("A new clearance request requires your approval.")
                .contains("pid-42");
        assertThat(message.getFrom()).isEqualTo("noreply@example.edu");
    }

    // ==================================================================
    // helpers
    // ==================================================================

    private static ExternalUser member(String username, String email) {
        ExternalUser user = new ExternalUser();
        user.setUsername(username);
        user.setEmail(email);
        return user;
    }

    private static NotificationMessage financeTaskMessage() {
        return NotificationMessage.builder()
                .type(NotificationMessage.Type.TASK_ASSIGNED)
                .processKey("clearanceLetterProcess")
                .processName("Clearance Letter")
                .processInstanceId("pid-1")
                .taskId("task-1")
                .stage("FINANCE")
                .department("Finance Department")
                .candidateGroup("FIN")
                .initiator(INITIATOR)
                .subject("[Clearance Letter] Approval required by Finance Department")
                .build();
    }
}
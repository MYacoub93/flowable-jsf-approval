package com.example.approval.notification.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Central configuration of the asynchronous e-mail notification
 * infrastructure.
 *
 * <p><b>Architecture rule (durable):</b> every workflow e-mail is executed
 * on the dedicated bounded executor {@code emailNotificationExecutor} -
 * NEVER on the Flowable/JSF request thread and never on an ad-hoc
 * {@code new Thread(...)}.</p>
 *
 * <pre>
 * Flowable task event (request thread)
 *      |
 * ClearanceTaskListener / ProcessHandler
 *      |
 * NotificationService.send(NotificationMessage)
 *      |  - resolve recipients (FLOWABLE_USERS_VW via SIS)
 *      |  - build subject + body
 *      v
 * AsyncEmailDispatcher.dispatch(EmailDispatchRequest)   <-- @Async boundary
 *      |
 *      v  (email-N worker thread, own transaction-free context)
 * JavaMailSender / SMTP
 * </pre>
 *
 * <p>{@code @EnableAsync} is declared here (next to the executor it
 * configures) so the {@code @Async} proxying of
 * {@code AsyncEmailDispatcher} is active without touching any other
 * configuration class.</p>
 *
 * <p><b>Rejection policy:</b> {@code AbortPolicy} + explicit handling in
 * {@code EmailNotificationService}: when the bounded queue is full (SMTP
 * outage) the submission is rejected, the rejection is LOGGED with the
 * notification identifiers and the workflow continues - e-mail is
 * infrastructure, not workflow state.</p>
 */
@Configuration
@EnableAsync
public class EmailNotificationAsyncConfig {

    private static final Logger log =
            LoggerFactory.getLogger(EmailNotificationAsyncConfig.class);

    /**
     * Name of the single shared executor used by
     * {@code AsyncEmailDispatcher} ({@code @Async("emailNotificationExecutor")}).
     * Referenced by tests to inject/await the same pool.
     */
    public static final String EXECUTOR_BEAN_NAME = "emailNotificationExecutor";

    /**
     * The ONE reusable, bounded thread pool for workflow e-mail sending.
     *
     * <p>See {@link EmailNotificationAsyncProperties} for the sizing
     * rationale of the defaults; everything is configurable via
     * {@code notification.async.*}.</p>
     */
    @Bean(EXECUTOR_BEAN_NAME)
    public ThreadPoolTaskExecutor emailNotificationExecutor(
            EmailNotificationAsyncProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getCorePoolSize());
        executor.setMaxPoolSize(properties.getMaxPoolSize());
        executor.setQueueCapacity(properties.getQueueCapacity());
        executor.setThreadNamePrefix(properties.getThreadNamePrefix());
        executor.setKeepAliveSeconds(properties.getKeepAliveSeconds());
        // Bounded queue -> explicit rejection -> logged + swallowed by the
        // caller; never CallerRunsPolicy (that would execute SMTP back on
        // the Flowable request thread) and never DiscardPolicy (silent loss).
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        // Wait for queued mails on application shutdown, but only up to the
        // configured timeout.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(properties.getAwaitTerminationSeconds());
        executor.initialize();
        log.info("Email notification executor '{}' initialized: core={}, max={}, "
                        + "boundedQueue={}, threadPrefix='{}', awaitTermination={}s",
                EXECUTOR_BEAN_NAME, properties.getCorePoolSize(),
                properties.getMaxPoolSize(), properties.getQueueCapacity(),
                properties.getThreadNamePrefix(), properties.getAwaitTerminationSeconds());
        return executor;
    }
}

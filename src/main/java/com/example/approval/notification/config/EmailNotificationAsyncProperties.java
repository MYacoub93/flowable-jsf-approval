package com.example.approval.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration of the DEDICATED e-mail notification executor
 * (prefix {@code notification.async.*} in application.yml) - the single
 * bounded thread pool every workflow e-mail is executed on.
 *
 * <pre>
 * notification:
 *   async:
 *     core-pool-size: 2
 *     max-pool-size: 4
 *     queue-capacity: 200
 *     thread-name-prefix: "email-"
 *     await-termination-seconds: 30
 * </pre>
 *
 * <p><b>Sizing rationale</b> (deliberately small - this is a workflow
 * notification pool, not a bulk-mail engine):</p>
 * <ul>
 *   <li>{@code core-pool-size: 2} - the normal workload is one mail per
 *       task event; two warm workers already smooth the burst a parallel
 *       department multi-instance stage produces;</li>
 *   <li>{@code max-pool-size: 4} - SMTP is the bottleneck (10s
 *       connect/read/write timeouts are configured on the sender), so more
 *       threads mostly add parallel SMTP sessions, not throughput;</li>
 *   <li>{@code queue-capacity: 200} - bounded on purpose: it covers the
 *       largest realistic burst (all departments of many simultaneous
 *       clearance requests) while an SMTP outage lasts, without allowing
 *       unbounded memory growth. When the queue is full the submission is
 *       rejected, which {@code EmailNotificationService} catches and logs -
 *       the workflow is never blocked (see
 *       {@code EmailNotificationAsyncConfig});</li>
 *   <li>{@code await-termination-seconds: 30} - graceful shutdown: queued
 *       mails get a chance to finish when the application stops, but
 *       shutdown never waits indefinitely.</li>
 * </ul>
 *
 * <p>All values are externalized so operations can tune them per
 * environment without a code change.</p>
 */
@Component
@ConfigurationProperties(prefix = "notification.async")
public class EmailNotificationAsyncProperties {

    /** Warm worker threads kept alive permanently. */
    private int corePoolSize = 2;

    /** Upper bound of worker threads (used once the queue is full). */
    private int maxPoolSize = 4;

    /** Bounded queue capacity; -1 would mean an unbounded queue (NOT wanted). */
    private int queueCapacity = 200;

    /** Prefix of the executor threads, e.g. {@code email-1}. */
    private String threadNamePrefix = "email-";

    /** Idle seconds before burst threads above core size are retired. */
    private int keepAliveSeconds = 60;

    /** Seconds shutdown waits for still-running / queued mails. */
    private int awaitTerminationSeconds = 30;

    public int getCorePoolSize() {
        return corePoolSize;
    }

    public void setCorePoolSize(int corePoolSize) {
        this.corePoolSize = corePoolSize;
    }

    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    public void setMaxPoolSize(int maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    public String getThreadNamePrefix() {
        return threadNamePrefix;
    }

    public void setThreadNamePrefix(String threadNamePrefix) {
        this.threadNamePrefix = threadNamePrefix;
    }

    public int getKeepAliveSeconds() {
        return keepAliveSeconds;
    }

    public void setKeepAliveSeconds(int keepAliveSeconds) {
        this.keepAliveSeconds = keepAliveSeconds;
    }

    public int getAwaitTerminationSeconds() {
        return awaitTerminationSeconds;
    }

    public void setAwaitTerminationSeconds(int awaitTerminationSeconds) {
        this.awaitTerminationSeconds = awaitTerminationSeconds;
    }
}
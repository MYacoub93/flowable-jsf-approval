package com.example.approval.notification.service;

import com.example.approval.notification.model.NotificationMessage;

/**
 * Global, process-agnostic notification port - usable by every Flowable
 * process (Clearance Letter, future approval processes, plain application
 * code).
 *
 * <p>Callers describe <b>what</b> happened via {@link NotificationMessage};
 * implementations decide <b>how</b> recipients are resolved and messages are
 * delivered (e-mail today, potentially SMS / push / webhooks later).</p>
 *
 * <p>Implementations must be <b>failure tolerant</b>: a delivery outage should
 * log an error, never break the Flowable process transaction.</p>
 */
public interface NotificationService {

    /**
     * Sends (or logs) one notification.
     *
     * @param message the fully described notification; never {@code null}
     */
    void send(NotificationMessage message);
}
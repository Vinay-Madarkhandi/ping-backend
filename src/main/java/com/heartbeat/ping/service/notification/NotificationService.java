package com.heartbeat.ping.service.notification;

import com.heartbeat.ping.modles.Incident;
import com.heartbeat.ping.modles.Monitor;

import java.time.Instant;

/**
 * Builds alert content and enqueues it into the durable email outbox (never sends inline).
 * Enqueue is idempotent per (incident, type) via the outbox dedupe key.
 */
public interface NotificationService {

    void enqueue(Monitor monitor, Incident incident, AlertType type, Instant now);

    /**
     * Enqueues a one-time warning that a monitor's TLS certificate is expiring soon. Idempotent
     * per (monitor, expiry instant) — renewing the certificate changes the expiry and so allows a
     * fresh warning, but the same certificate is never warned about twice.
     */
    void enqueueSslExpiryWarning(Monitor monitor, Instant expiresAt, long daysRemaining, Instant now);
}

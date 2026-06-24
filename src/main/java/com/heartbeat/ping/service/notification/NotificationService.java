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
}

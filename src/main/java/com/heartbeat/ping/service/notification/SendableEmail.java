package com.heartbeat.ping.service.notification;

import java.util.UUID;

/** Detached snapshot of an outbox row to send, so SMTP I/O happens outside any transaction/lock. */
public record SendableEmail(UUID id, String recipient, String subject, String body) {
}

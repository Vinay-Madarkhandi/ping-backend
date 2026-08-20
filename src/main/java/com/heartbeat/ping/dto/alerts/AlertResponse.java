package com.heartbeat.ping.dto.alerts;

import com.heartbeat.ping.modles.EmailOutbox;

import java.time.Instant;
import java.util.UUID;

/**
 * One alert delivery attempt, projected from the durable outbox row.
 *
 * <p>Answers the question users could not previously ask at all: "was I actually notified?" It also
 * surfaces dead letters, which until now failed silently — the runbook's only remedy was for an
 * operator to watch a Prometheus counter.
 *
 * @param type     DOWN or RECOVERY, recovered from the outbox dedupe key ({@code <incidentId>:<type>}).
 * @param status   PENDING (queued or awaiting retry), SENT, or FAILED (dead-lettered).
 * @param lastError the most recent SMTP failure, when delivery has been failing.
 */
public record AlertResponse(
        UUID id,
        UUID monitorId,
        String type,
        String recipient,
        String subject,
        String status,
        int attempts,
        int maxAttempts,
        String lastError,
        Instant createdAt,
        Instant sentAt
) {

    public static AlertResponse from(EmailOutbox email) {
        return new AlertResponse(
                email.getId(),
                email.getMonitorId(),
                typeOf(email.getDedupeKey()),
                email.getRecipient(),
                email.getSubject(),
                email.getStatus().name(),
                email.getAttempts(),
                email.getMaxAttempts(),
                email.getLastError(),
                email.getCreatedAt(),
                email.getSentAt());
    }

    /**
     * The dedupe key is {@code <incidentId>:<ALERT_TYPE>}. Reading the suffix avoids denormalising the
     * type into its own column purely for display.
     */
    private static String typeOf(String dedupeKey) {
        if (dedupeKey == null) {
            return null;
        }
        int separator = dedupeKey.lastIndexOf(':');
        return separator >= 0 && separator < dedupeKey.length() - 1
                ? dedupeKey.substring(separator + 1)
                : null;
    }
}

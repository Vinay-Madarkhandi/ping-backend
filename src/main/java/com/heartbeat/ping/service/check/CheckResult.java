package com.heartbeat.ping.service.check;

import com.heartbeat.ping.modles.ProbeOutcome;

import java.time.Instant;

/**
 * Immutable outcome of a single health check. {@code statusCode} is 0 when the request never
 * produced an HTTP response (timeout, connection error, infrastructure failure). {@code
 * sslExpiresAt} is set only for HTTPS checks that completed a TLS handshake, captured from the
 * check's own session — never null-checked by callers that don't care about it.
 */
public record CheckResult(
        ProbeOutcome outcome, int statusCode, long responseTimeMs, String errorMessage, Instant sslExpiresAt
) {

    public static CheckResult up(int statusCode, long responseTimeMs) {
        return new CheckResult(ProbeOutcome.UP, statusCode, responseTimeMs, null, null);
    }

    public static CheckResult down(int statusCode, long responseTimeMs, String errorMessage) {
        return new CheckResult(ProbeOutcome.DOWN, statusCode, responseTimeMs, errorMessage, null);
    }

    /** Our own infrastructure failed to complete the probe — never treated as a target failure. */
    public static CheckResult inconclusive(long responseTimeMs, String errorMessage) {
        return new CheckResult(ProbeOutcome.INCONCLUSIVE, 0, responseTimeMs, errorMessage, null);
    }

    public CheckResult withSslExpiresAt(Instant sslExpiresAt) {
        return new CheckResult(outcome, statusCode, responseTimeMs, errorMessage, sslExpiresAt);
    }

    public boolean up() {
        return outcome == ProbeOutcome.UP;
    }

    public boolean inconclusive() {
        return outcome == ProbeOutcome.INCONCLUSIVE;
    }
}

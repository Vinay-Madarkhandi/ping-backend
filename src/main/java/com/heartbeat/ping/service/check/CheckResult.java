package com.heartbeat.ping.service.check;

import com.heartbeat.ping.modles.ProbeOutcome;

/**
 * Immutable outcome of a single health check. {@code statusCode} is 0 when the request never
 * produced an HTTP response (timeout, connection error, infrastructure failure).
 */
public record CheckResult(ProbeOutcome outcome, int statusCode, long responseTimeMs, String errorMessage) {

    public static CheckResult up(int statusCode, long responseTimeMs) {
        return new CheckResult(ProbeOutcome.UP, statusCode, responseTimeMs, null);
    }

    public static CheckResult down(int statusCode, long responseTimeMs, String errorMessage) {
        return new CheckResult(ProbeOutcome.DOWN, statusCode, responseTimeMs, errorMessage);
    }

    /** Our own infrastructure failed to complete the probe — never treated as a target failure. */
    public static CheckResult inconclusive(long responseTimeMs, String errorMessage) {
        return new CheckResult(ProbeOutcome.INCONCLUSIVE, 0, responseTimeMs, errorMessage);
    }

    public boolean up() {
        return outcome == ProbeOutcome.UP;
    }

    public boolean inconclusive() {
        return outcome == ProbeOutcome.INCONCLUSIVE;
    }
}

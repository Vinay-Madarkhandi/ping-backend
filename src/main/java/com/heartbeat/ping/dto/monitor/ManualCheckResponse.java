package com.heartbeat.ping.dto.monitor;

import com.heartbeat.ping.modles.ProbeOutcome;

import java.time.Instant;

/**
 * Result of an on-demand check ({@code POST /api/v1/monitors/{id}/check-now}), so the UI can show
 * "did my fix work?" immediately instead of waiting for the next scheduled probe.
 *
 * <p>The probe runs through the normal pipeline, so it also updates state, logs, quota and alerts —
 * it is a real observation, not a simulation.
 */
public record ManualCheckResponse(
        ProbeOutcome outcome,
        int statusCode,
        long responseTimeMs,
        String errorMessage,
        Instant checkedAt
) {
}

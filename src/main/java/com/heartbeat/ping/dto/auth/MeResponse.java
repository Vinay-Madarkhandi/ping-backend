package com.heartbeat.ping.dto.auth;

import java.time.Instant;
import java.util.UUID;

/**
 * The current user's profile, plan and subscription state — everything the frontend needs to render
 * plan-gated UI (current plan, renewal date) and pricing without hardcoding limits.
 */
public record MeResponse(
        UUID userId,
        String email,
        String userName,
        String subscriptionStatus,
        Instant subscriptionStartAt,
        Instant subscriptionEndAt,
        PlanDto plan
) {
    public record PlanDto(
            String name,
            int maxMonitors,
            int minIntervalMs,
            int maxTimeoutMs,
            long monthlyCheckQuota,
            int retentionDays,
            int alertCooldownSeconds,
            int maxAlertsPerDay,
            long priceAmount,
            String currency,
            int durationDays
    ) {
    }
}

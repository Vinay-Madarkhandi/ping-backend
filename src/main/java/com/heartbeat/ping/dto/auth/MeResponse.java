package com.heartbeat.ping.dto.auth;

import com.heartbeat.ping.dto.plan.PlanResponse;

import java.time.Instant;
import java.util.UUID;

/**
 * The current user's profile, plan and subscription state — everything the frontend needs to render
 * plan-gated UI (current plan, renewal date) and pricing without hardcoding limits.
 *
 * <p>The plan is the shared {@link PlanResponse} shape also returned by {@code GET /api/v1/plans},
 * so "my plan" and "the catalog" can never disagree on field names.
 */
public record MeResponse(
        UUID userId,
        String email,
        String userName,
        boolean emailVerified,
        String subscriptionStatus,
        Instant subscriptionStartAt,
        Instant subscriptionEndAt,
        PlanResponse plan
) {
}

package com.heartbeat.ping.dto.plan;

import com.heartbeat.ping.modles.Plan;

/**
 * The canonical wire shape of a subscription tier and its limits. Used both by the plan catalog
 * ({@code GET /api/v1/plans}) and by {@code GET /api/v1/auth/me}, so the frontend has exactly one
 * plan type to model instead of two that can drift apart.
 */
public record PlanResponse(
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

    public static PlanResponse from(Plan plan) {
        return new PlanResponse(
                plan.getName(),
                plan.getMaxMonitors(),
                plan.getMinIntervalMs(),
                plan.getMaxTimeoutMs(),
                plan.getMonthlyCheckQuota(),
                plan.getRetentionDays(),
                plan.getAlertCooldownSeconds(),
                plan.getMaxAlertsPerDay(),
                plan.getPriceAmount(),
                plan.getCurrency(),
                plan.getDurationDays());
    }
}

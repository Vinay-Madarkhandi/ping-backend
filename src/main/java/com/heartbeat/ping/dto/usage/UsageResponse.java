package com.heartbeat.ping.dto.usage;

/**
 * The current user's consumption against their plan, for the dashboard usage meter and quota banner.
 *
 * @param overQuota true once {@code checksThisMonth} has reached the plan's monthly quota, at which
 *                  point the enforcement job stops scheduling this user's monitors.
 */
public record UsageResponse(
        long monitorCount,
        long checksThisMonth,
        int alertsToday,
        boolean overQuota
) {
}

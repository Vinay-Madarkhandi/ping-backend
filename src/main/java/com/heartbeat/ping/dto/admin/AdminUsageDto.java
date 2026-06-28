package com.heartbeat.ping.dto.admin;

import java.util.UUID;

/** Per-user resource consumption snapshot for the admin usage view. */
public record AdminUsageDto(
        UUID userId,
        String email,
        String plan,
        long monitorCount,
        long checksThisMonth,
        int alertsToday,
        boolean overQuota
) {
}

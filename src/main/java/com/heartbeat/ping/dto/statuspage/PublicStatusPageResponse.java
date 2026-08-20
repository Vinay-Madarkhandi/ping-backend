package com.heartbeat.ping.dto.statuspage;

import java.time.Instant;
import java.util.List;

/**
 * The full unauthenticated payload served at GET /api/v1/public/status-pages/{slug}.
 * {@code overallStatus} is one of OPERATIONAL / DEGRADED / PARTIAL_OUTAGE / MAJOR_OUTAGE.
 */
public record PublicStatusPageResponse(
        String title,
        String description,
        String logoUrl,
        String overallStatus,
        List<PublicMonitorStatus> monitors,
        Instant updatedAt
) {
}

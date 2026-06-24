package com.heartbeat.ping.dto.analytics;

import com.heartbeat.ping.service.uptime.UptimeResult;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/** Duration-based uptime over a window. {@code uptimePercentage} is null when no time was monitored. */
@Getter
@Builder
public class UptimeResponse {

    private UUID monitorId;
    private Instant windowStart;
    private Instant windowEnd;
    private Double uptimePercentage;
    private long windowSeconds;
    private long monitoredSeconds;
    private long downSeconds;
    private long pausedSeconds;
    private long gapSeconds;

    public static UptimeResponse from(UUID monitorId, UptimeResult r) {
        return UptimeResponse.builder()
                .monitorId(monitorId)
                .windowStart(r.windowStart())
                .windowEnd(r.windowEnd())
                .uptimePercentage(r.uptimePercentage())
                .windowSeconds(r.windowSeconds())
                .monitoredSeconds(r.monitoredSeconds())
                .downSeconds(r.downSeconds())
                .pausedSeconds(r.pausedSeconds())
                .gapSeconds(r.gapSeconds())
                .build();
    }
}

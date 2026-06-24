package com.heartbeat.ping.service.uptime;

import java.time.Instant;

/**
 * Outcome of a duration-based uptime computation over a window.
 *
 * @param uptimePercentage (monitoredSeconds - downSeconds) / monitoredSeconds * 100, or
 *                         {@code null} when there was no monitored time (fully paused / no data).
 */
public record UptimeResult(
        Double uptimePercentage,
        Instant windowStart,
        Instant windowEnd,
        long windowSeconds,
        long monitoredSeconds,
        long downSeconds,
        long pausedSeconds,
        long gapSeconds
) {
}

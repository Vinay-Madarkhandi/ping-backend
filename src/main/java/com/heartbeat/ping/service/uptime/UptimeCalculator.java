package com.heartbeat.ping.service.uptime;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Pure, duration-based uptime calculation. Uptime is measured as a fraction of <em>monitored</em>
 * time that the monitor was up — not as a ratio of successful checks.
 *
 * <p>Disjoint precedence when categorising window time (highest wins):
 * <ol>
 *   <li><b>Paused</b> — intentional; excluded from the denominator.</li>
 *   <li><b>Gap</b> (missing data / scheduler outage, outside paused) — excluded; we never claim
 *       uptime we did not measure. This also excludes any incident time overlapping a gap.</li>
 *   <li><b>Down</b> (confirmed outage, outside paused/gap) — counts against uptime.</li>
 *   <li><b>Up</b> — the remaining monitored time.</li>
 * </ol>
 *
 * <pre>
 *   monitored = window - paused - gap
 *   uptime%   = monitored &lt;= 0 ? null : (monitored - down) / monitored * 100
 * </pre>
 *
 * All interval boundaries are expected to come from the database clock (see DatabaseClock).
 */
@Component
public class UptimeCalculator {

    public UptimeResult compute(Instant windowStart,
                                Instant windowEnd,
                                List<TimeInterval> downIntervals,
                                List<TimeInterval> pausedIntervals,
                                List<TimeInterval> gapIntervals) {

        List<TimeInterval> paused = Intervals.merge(Intervals.clip(pausedIntervals, windowStart, windowEnd));
        List<TimeInterval> gap = Intervals.subtract(
                Intervals.merge(Intervals.clip(gapIntervals, windowStart, windowEnd)), paused);
        List<TimeInterval> down = Intervals.subtract(
                Intervals.subtract(
                        Intervals.merge(Intervals.clip(downIntervals, windowStart, windowEnd)), paused),
                gap);

        long windowSeconds = Duration.between(windowStart, windowEnd).getSeconds();
        long pausedSeconds = Intervals.totalSeconds(paused);
        long gapSeconds = Intervals.totalSeconds(gap);
        long downSeconds = Intervals.totalSeconds(down);
        long monitoredSeconds = windowSeconds - pausedSeconds - gapSeconds;

        Double uptimePercentage = monitoredSeconds <= 0
                ? null
                : (monitoredSeconds - downSeconds) * 100.0 / monitoredSeconds;

        return new UptimeResult(uptimePercentage, windowStart, windowEnd,
                windowSeconds, Math.max(0, monitoredSeconds), downSeconds, pausedSeconds, gapSeconds);
    }

    /** Total down seconds within [start, end) from raw incident intervals (used by daily rollups). */
    public long downtimeSeconds(Instant start, Instant end, List<TimeInterval> downIntervals) {
        return Intervals.totalSeconds(Intervals.merge(Intervals.clip(downIntervals, start, end)));
    }
}

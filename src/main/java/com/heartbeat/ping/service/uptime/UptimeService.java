package com.heartbeat.ping.service.uptime;

import com.heartbeat.ping.config.properties.RetentionProperties;
import com.heartbeat.ping.config.properties.UptimeProperties;
import com.heartbeat.ping.modles.Monitor;
import com.heartbeat.ping.repository.IncidentRepository;
import com.heartbeat.ping.repository.MonitorDailyStatsRepository;
import com.heartbeat.ping.repository.MonitorLogsRepository;
import com.heartbeat.ping.repository.MonitorPauseWindowRepository;
import com.heartbeat.ping.repository.MonitorRepository;
import com.heartbeat.ping.service.time.DatabaseClock;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Computes duration-based uptime over a window. Gathers the three exclusion/penalty interval sets
 * — DOWN (incidents), PAUSED (pause windows), GAP (coverage gaps in the check log) — and delegates
 * the arithmetic to {@link UptimeCalculator}. All timestamps come from the database clock.
 *
 * <p>Edge cases:
 * <ul>
 *   <li><b>Open incidents / pause windows</b> are extended to the window end.</li>
 *   <li><b>Archived monitors</b>: the window end is capped at {@code deletedAt} (no monitoring after).</li>
 *   <li><b>Window start</b> is clamped to the monitor's creation time (no uptime before it existed).</li>
 *   <li><b>Missing data</b>: gaps between checks longer than {@code gapMultiplier x interval}
 *       (and the leading/trailing edges) are excluded from the denominator.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class UptimeService {

    private final MonitorRepository monitorRepository;
    private final IncidentRepository incidentRepository;
    private final MonitorPauseWindowRepository pauseWindowRepository;
    private final MonitorLogsRepository logsRepository;
    private final MonitorDailyStatsRepository dailyStatsRepository;
    private final UptimeCalculator calculator;
    private final UptimeProperties props;
    private final RetentionProperties retentionProps;
    private final DatabaseClock clock;

    @Transactional(readOnly = true)
    public UptimeResult uptime(UUID monitorId, UUID userId, Duration window) {
        Monitor monitor = monitorRepository.findByIdAndUser_Id(monitorId, userId)
                .orElseThrow(() -> new AccessDeniedException("Monitor not found for this user"));

        Instant now = clock.now();
        Instant windowEnd = effectiveEnd(monitor, now);
        Instant windowStart = max(windowEnd.minus(window), toInstant(monitor.getCreatedAt()));

        if (!windowStart.isBefore(windowEnd)) {
            // Window collapses (e.g. archived/created after the window) — nothing to measure.
            return new UptimeResult(null, windowStart, windowEnd, 0, 0, 0, 0, 0);
        }

        // Incidents (down) and pause windows are never purged, so they cover the full window.
        List<TimeInterval> down = incidentRepository.findOverlapping(monitorId, windowStart, windowEnd).stream()
                .map(i -> new TimeInterval(i.getStartedAt(),
                        i.getResolvedAt() != null ? i.getResolvedAt() : windowEnd))
                .toList();

        List<TimeInterval> paused = pauseWindowRepository.findOverlapping(monitorId, windowStart, windowEnd).stream()
                .map(w -> new TimeInterval(w.getPausedAt(),
                        w.getResumedAt() != null ? w.getResumedAt() : windowEnd))
                .toList();

        List<TimeInterval> gaps = detectGaps(monitor, windowStart, windowEnd, now);

        return calculator.compute(windowStart, windowEnd, down, paused, gaps);
    }

    private Instant effectiveEnd(Monitor monitor, Instant now) {
        Instant deletedAt = monitor.getDeletedAt();
        return (deletedAt != null && deletedAt.isBefore(now)) ? deletedAt : now;
    }

    /**
     * Coverage gaps across the window. Raw logs only exist for the last {@code retention.days}, so
     * the window is split at the raw-log cutoff: the recent part uses per-check timestamps; the
     * purged-history part uses daily rollups (a day with no rollup row is a full-day gap).
     */
    private List<TimeInterval> detectGaps(Monitor monitor, Instant windowStart, Instant windowEnd, Instant now) {
        Instant logCutoff = now.minus(Duration.ofDays(retentionProps.getDays()));
        Instant recentStart = max(windowStart, logCutoff);

        List<TimeInterval> gaps = new ArrayList<>();
        if (windowStart.isBefore(recentStart)) {
            gaps.addAll(gapsFromRollups(monitor.getId(), windowStart, recentStart));
        }
        gaps.addAll(gapsFromLogs(monitor, recentStart, windowEnd));
        return gaps;
    }

    /** Recent region: gaps between consecutive checks (and leading/trailing edges) beyond the threshold. */
    private List<TimeInterval> gapsFromLogs(Monitor monitor, Instant from, Instant to) {
        if (!from.isBefore(to)) {
            return List.of();
        }
        Duration threshold = gapThreshold(monitor);
        List<LocalDateTime> times = logsRepository.findCheckTimes(monitor.getId(), toLocal(from), toLocal(to));

        List<TimeInterval> gaps = new ArrayList<>();
        Instant cursor = from;
        for (LocalDateTime time : times) {
            Instant checkAt = time.toInstant(ZoneOffset.UTC);
            if (Duration.between(cursor, checkAt).compareTo(threshold) > 0) {
                gaps.add(new TimeInterval(cursor, checkAt));
            }
            if (checkAt.isAfter(cursor)) {
                cursor = checkAt;
            }
        }
        if (Duration.between(cursor, to).compareTo(threshold) > 0) {
            gaps.add(new TimeInterval(cursor, to));
        }
        return gaps;
    }

    /** Purged-history region: any UTC day in [from, to) with no daily-stats row is treated as a gap. */
    private List<TimeInterval> gapsFromRollups(UUID monitorId, Instant from, Instant to) {
        LocalDate fromDay = LocalDate.ofInstant(from, ZoneOffset.UTC);
        LocalDate toDay = LocalDate.ofInstant(to, ZoneOffset.UTC).plusDays(1);
        Set<LocalDate> present = Set.copyOf(dailyStatsRepository.findStatDays(monitorId, fromDay, toDay));

        List<TimeInterval> gaps = new ArrayList<>();
        for (LocalDate day = fromDay; day.isBefore(toDay); day = day.plusDays(1)) {
            if (present.contains(day)) {
                continue; // had data that day -> treat as covered
            }
            Instant dayStart = max(day.atStartOfDay().toInstant(ZoneOffset.UTC), from);
            Instant dayEnd = minInstant(day.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC), to);
            if (dayStart.isBefore(dayEnd)) {
                gaps.add(new TimeInterval(dayStart, dayEnd));
            }
        }
        return gaps;
    }

    private Duration gapThreshold(Monitor monitor) {
        Duration cadence = Duration.ofMillis((long) monitor.getIntervalMilliseconds() * props.getGapMultiplier());
        return cadence.compareTo(props.getMinGapThreshold()) > 0 ? cadence : props.getMinGapThreshold();
    }

    private Instant max(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }

    private Instant minInstant(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }

    private Instant toInstant(LocalDateTime ldt) {
        return ldt.toInstant(ZoneOffset.UTC);
    }

    private LocalDateTime toLocal(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}

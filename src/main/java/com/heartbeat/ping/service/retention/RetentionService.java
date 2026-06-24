package com.heartbeat.ping.service.retention;

import com.heartbeat.ping.config.properties.RetentionProperties;
import com.heartbeat.ping.modles.MonitorDailyStats;
import com.heartbeat.ping.modles.MonitorLogs;
import com.heartbeat.ping.repository.IncidentRepository;
import com.heartbeat.ping.repository.MonitorDailyStatsRepository;
import com.heartbeat.ping.repository.MonitorLogsRepository;
import com.heartbeat.ping.service.time.DatabaseClock;
import com.heartbeat.ping.service.uptime.TimeInterval;
import com.heartbeat.ping.service.uptime.UptimeCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Rolls the previous day's raw logs into {@code monitor_daily_stats} (so history survives the
 * purge), then deletes raw logs older than the retention window. All time on the database clock.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetentionService {

    private final MonitorLogsRepository logsRepository;
    private final IncidentRepository incidentRepository;
    private final MonitorDailyStatsRepository dailyStatsRepository;
    private final UptimeCalculator calculator;
    private final RetentionProperties props;
    private final DatabaseClock clock;

    @Transactional
    public void runRetention() {
        Instant now = clock.now();
        LocalDate yesterday = LocalDate.ofInstant(now, ZoneOffset.UTC).minusDays(1);
        rollupDay(yesterday);

        LocalDateTime cutoff = LocalDateTime.ofInstant(now, ZoneOffset.UTC).minusDays(props.getDays());
        int purged = logsRepository.deleteByCheckedAtBefore(cutoff);
        log.info("Retention: rolled up {}, purged {} logs older than {} days", yesterday, purged, props.getDays());
    }

    private void rollupDay(LocalDate day) {
        LocalDateTime from = day.atStartOfDay();
        LocalDateTime to = day.plusDays(1).atStartOfDay();
        Instant fromInstant = from.toInstant(ZoneOffset.UTC);
        Instant toInstant = to.toInstant(ZoneOffset.UTC);

        for (UUID monitorId : logsRepository.findMonitorIdsWithLogsBetween(from, to)) {
            if (dailyStatsRepository.existsByMonitorIdAndDay(monitorId, day)) {
                continue; // idempotent — already rolled up
            }
            List<MonitorLogs> logs = logsRepository.findByMonitorIdAndCheckedAtBetween(monitorId, from, to);
            if (logs.isEmpty()) {
                continue;
            }

            int checks = logs.size();
            int ups = (int) logs.stream().filter(MonitorLogs::isUp).count();
            double avgMs = logs.stream().mapToInt(MonitorLogs::getResponseTimeInMilli).average().orElse(0);
            long downtime = downtimeSeconds(monitorId, fromInstant, toInstant);

            dailyStatsRepository.save(MonitorDailyStats.builder()
                    .monitorId(monitorId)
                    .day(day)
                    .totalChecks(checks)
                    .totalUp(ups)
                    .totalDown(checks - ups)
                    .avgResponseMs(avgMs)
                    .downtimeSeconds(downtime)
                    .build());
        }
    }

    private long downtimeSeconds(UUID monitorId, Instant from, Instant to) {
        List<TimeInterval> down = incidentRepository.findOverlapping(monitorId, from, to).stream()
                .map(i -> new TimeInterval(i.getStartedAt(), i.getResolvedAt() != null ? i.getResolvedAt() : to))
                .toList();
        return calculator.downtimeSeconds(from, to, down);
    }
}

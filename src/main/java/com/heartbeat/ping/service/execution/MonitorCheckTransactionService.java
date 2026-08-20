package com.heartbeat.ping.service.execution;

import com.heartbeat.ping.modles.Monitor;
import com.heartbeat.ping.modles.MonitorLogs;
import com.heartbeat.ping.modles.MonitorState;
import com.heartbeat.ping.modles.MonitorStatus;
import com.heartbeat.ping.repository.MonitorLogsRepository;
import com.heartbeat.ping.repository.MonitorRepository;
import com.heartbeat.ping.repository.MonitorStatusRepository;
import com.heartbeat.ping.repository.UserUsageRepository;
import com.heartbeat.ping.service.alert.AlertEngine;
import com.heartbeat.ping.service.check.CheckResult;
import com.heartbeat.ping.service.check.CheckSpec;
import com.heartbeat.ping.service.time.DatabaseClock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

/**
 * Owns the short-lived database transactions around a check: loading the immutable {@link CheckSpec}
 * before the probe, and persisting the result afterwards. The HTTP probe runs entirely outside
 * these transactions (no JDBC connection held during network I/O).
 *
 * <p>Lock order in {@link #recordResult}: the {@code monitor_status} row is locked FOR UPDATE
 * <em>first</em> (the alert FSM's linearization point) before the log insert touches the monitor
 * via FK, keeping the global hierarchy {@code monitor_status -> monitor -> incident -> email_outbox}.
 */
@Service
@RequiredArgsConstructor
public class MonitorCheckTransactionService {

    private final MonitorRepository monitorRepository;
    private final MonitorLogsRepository monitorLogsRepository;
    private final MonitorStatusRepository statusRepository;
    private final UserUsageRepository userUsageRepository;
    private final AlertEngine alertEngine;
    private final DatabaseClock clock;

    @Transactional(readOnly = true)
    public Optional<CheckSpec> loadSpec(UUID monitorId) {
        return monitorRepository.findById(monitorId)
                .filter(monitor -> monitor.getDeletedAt() == null)
                .map(CheckSpec::from);
    }

    @Transactional
    public void recordResult(UUID monitorId, UUID userId, CheckResult result) {
        Monitor monitor = monitorRepository.findById(monitorId).orElse(null);
        if (monitor == null || monitor.getDeletedAt() != null) {
            return; // deleted/archived between claim and persistence — discard
        }

        Instant now = clock.now();
        // Lock the status row first (FSM linearization point) — before the log insert's FK touch.
        MonitorStatus status = statusRepository.findByIdForUpdate(monitorId)
                .orElseGet(() -> createStatus(monitor));

        monitorLogsRepository.save(toLog(monitor, result, now));

        // Every executed check consumes quota, regardless of outcome.
        if (userId != null) {
            LocalDate periodMonth = LocalDate.ofInstant(now, ZoneOffset.UTC).withDayOfMonth(1);
            userUsageRepository.incrementChecks(userId, periodMonth);
        }

        // Paused monitors record the log but skip the FSM; inconclusive (infra) results never
        // drive state, incidents or alerts (handled inside the alert engine).
        if (!monitor.isPaused()) {
            if (result.sslExpiresAt() != null) {
                status.setSslCertExpiresAt(LocalDateTime.ofInstant(result.sslExpiresAt(), ZoneOffset.UTC));
            }
            alertEngine.process(monitor, status, result, now);
            statusRepository.save(status);
        }

        reschedule(monitor, now);
        monitorRepository.save(monitor);
    }

    private MonitorStatus createStatus(Monitor monitor) {
        MonitorStatus status = new MonitorStatus();
        status.setMonitor(monitor); // sets shared id via @MapsId
        status.setUptimePercentage(100.0);
        status.setCurrentState(MonitorState.UNKNOWN);
        return status;
    }

    private MonitorLogs toLog(Monitor monitor, CheckResult result, Instant now) {
        return MonitorLogs.builder()
                .monitor(monitor)
                .statusCode(result.statusCode())
                .responseTimeInMilli((int) result.responseTimeMs())
                .isUp(result.up())
                .outcome(result.outcome())
                .errorMessage(result.errorMessage())
                .checkedAt(LocalDateTime.ofInstant(now, ZoneOffset.UTC))
                .build();
    }

    /** Overwrites the lease with the true next due time (on the DB clock) now that the check is done. */
    private void reschedule(Monitor monitor, Instant now) {
        monitor.setNextCheckAt(now.plusMillis(monitor.getIntervalMilliseconds()));
    }
}

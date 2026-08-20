package com.heartbeat.ping.service;

import com.heartbeat.ping.dto.analytics.MonitorStatusResponse;
import com.heartbeat.ping.modles.Incident;
import com.heartbeat.ping.modles.Monitor;
import com.heartbeat.ping.modles.MonitorLogs;
import com.heartbeat.ping.modles.MonitorState;
import com.heartbeat.ping.modles.MonitorStatus;
import com.heartbeat.ping.repository.MonitorLogsRepository;
import com.heartbeat.ping.repository.MonitorRepository;
import com.heartbeat.ping.repository.MonitorStatusRepository;
import com.heartbeat.ping.service.incident.IncidentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MonitorAnalyticService {

    private final MonitorRepository monitorRepository;
    private final MonitorLogsRepository logsRepository;
    private final MonitorStatusRepository monitorStatusRepository;
    private final IncidentService incidentService;

    public Page<Incident> getIncidents(UUID monitorId, UUID userId, Pageable pageable) {
        if (!monitorRepository.existsByIdAndUser_Id(monitorId, userId)) {
            throw new AccessDeniedException("Monitor not found for this user");
        }
        return incidentService.history(monitorId, pageable);
    }

    /** Bounded to the most recent 5000 incidents so a CSV export can never run away. */
    public List<Incident> getIncidentsForExport(UUID monitorId, UUID userId) {
        if (!monitorRepository.existsByIdAndUser_Id(monitorId, userId)) {
            throw new AccessDeniedException("Monitor not found for this user");
        }
        return incidentService.history(monitorId, PageRequest.of(0, 5000)).getContent();
    }

    private static final Duration MAX_EXPORT_RANGE = Duration.ofDays(90);

    /**
     * Raw check logs for a CSV export. The range is capped at 90 days so a single request can't be
     * used to pull a user's entire log history in one unbounded query.
     */
    public List<MonitorLogs> getMonitorLogsForExport(
            UUID monitorId, UUID userId, LocalDateTime from, LocalDateTime to
    ) {
        Monitor monitor = monitorRepository
                .findById(monitorId)
                .orElseThrow(() -> new AccessDeniedException("Monitor not found for this user"));

        if (!monitor.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("Monitor not found for this user");
        }

        if (!to.isAfter(from)) {
            throw new IllegalArgumentException("'to' must be after 'from'");
        }
        if (Duration.between(from, to).compareTo(MAX_EXPORT_RANGE) > 0) {
            throw new IllegalArgumentException("Export range must be at most 90 days");
        }

        return logsRepository.findByMonitorIdAndCheckedAtBetween(monitorId, from, to);
    }

    public Page<MonitorLogs> getMonitorLogs(
            UUID monitorId,
            UUID userId,
            Pageable pageable
    ) {
        Monitor monitor = monitorRepository
                .findById(monitorId)
                .orElseThrow(() -> new AccessDeniedException("Monitor not found for this user"));

        if (!monitor.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("Monitor not found for this user");
        }

        return logsRepository.findByMonitorIdOrderByCheckedAtDesc(
                monitorId,
                pageable
        );
    }

    public MonitorStatusResponse getMonitorStatus(UUID monitorId, UUID userId) {
        Monitor monitor = monitorRepository.findByIdAndUser_Id(monitorId, userId)
                .orElseThrow(() -> new AccessDeniedException("Monitor not found for this user"));

        MonitorStatus status = monitorStatusRepository.findById(monitorId)
                .orElseThrow(() ->
                        new IllegalStateException("Monitor status not initialized yet")
                );
        // Derive from the FSM state (not the last raw log) so an INCONCLUSIVE check never flips "up".
        boolean isUp = status.getCurrentState() == MonitorState.UP;

        String currentState = status.getCurrentState().name();
        // Precedence: quota block (system-imposed) > paused (user-imposed) > health state.
        String displayState;
        if (monitor.isQuotaBlocked()) {
            displayState = "QUOTA_EXCEEDED";
        } else if (monitor.isPaused()) {
            displayState = "PAUSED";
        } else {
            displayState = currentState;
        }

        LocalDateTime sslCertExpiresAt = status.getSslCertExpiresAt();

        return MonitorStatusResponse.builder()
                .isUp(isUp)
                .currentState(currentState)
                .displayState(displayState)
                .quotaBlocked(monitor.isQuotaBlocked())
                .totalChecks(status.getTotalChecks())
                .totalUp(status.getTotalUp())
                .totalDown(status.getTotalDown())
                .uptimePercentage(status.getUptimePercentage())
                .lastDowntimeAt(status.getLastDowntimeAt())
                .lastCheckedAt(status.getUpdatedAt())
                .sslCertExpiresAt(sslCertExpiresAt)
                .sslDaysRemaining(sslCertExpiresAt != null
                        ? ChronoUnit.DAYS.between(LocalDateTime.now(), sslCertExpiresAt)
                        : null)
                .build();
    }
}

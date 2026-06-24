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
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

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

    public Page<MonitorLogs> getMonitorLogs(
            UUID monitorId,
            UUID userId,
            Pageable pageable
    ) {
        Monitor monitor = monitorRepository
                .findById(monitorId)
                .orElseThrow(() -> new RuntimeException("Monitor not found"));

        if (!monitor.getUser().getId().equals(userId)) {
            throw new RuntimeException("Unauthorized");
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
        String displayState = monitor.isPaused() ? "PAUSED" : currentState;

        return MonitorStatusResponse.builder()
                .isUp(isUp)
                .currentState(currentState)
                .displayState(displayState)
                .totalChecks(status.getTotalChecks())
                .totalUp(status.getTotalUp())
                .totalDown(status.getTotalDown())
                .uptimePercentage(status.getUptimePercentage())
                .lastDowntimeAt(status.getLastDowntimeAt())
                .lastCheckedAt(status.getUpdatedAt())
                .build();
    }
}

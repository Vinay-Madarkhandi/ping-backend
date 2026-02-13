package com.heartbeat.ping.service;

import com.heartbeat.ping.dto.analytics.MonitorStatusResponse;
import com.heartbeat.ping.modles.Monitor;
import com.heartbeat.ping.modles.MonitorLogs;
import com.heartbeat.ping.modles.MonitorStatus;
import com.heartbeat.ping.repository.MonitorLogsRepository;
import com.heartbeat.ping.repository.MonitorRepository;
import com.heartbeat.ping.repository.MonitorStatusRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class MonitorAnalyticService {

    private final MonitorRepository monitorRepository;
    private final MonitorLogsRepository logsRepository;
    private final MonitorStatusRepository monitorStatusRepository;

    public MonitorAnalyticService(MonitorRepository monitorRepository, MonitorLogsRepository logsRepository, MonitorStatusRepository monitorStatusRepository){
        this.monitorRepository = monitorRepository;
        this.logsRepository = logsRepository;
        this.monitorStatusRepository = monitorStatusRepository;
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
        boolean ownsMonitor = monitorRepository.existsByIdAndUser_Id(monitorId, userId);

        if (!ownsMonitor) {
            throw new AccessDeniedException("Monitor not found for this user");
        }

        MonitorStatus status = monitorStatusRepository.findById(monitorId)
                .orElseThrow(() ->
                        new IllegalStateException("Monitor status not initialized yet")
                );

        return MonitorStatusResponse.builder()
                .isUp(status.getTotalChecks() == 0
                        ? true
                        : status.getLastDowntimeAt() == null
                        || status.getLastDowntimeAt().isBefore(status.getUpdatedAt()))
                .totalChecks(status.getTotalChecks())
                .totalUp(status.getTotalUp())
                .totalDown(status.getTotalDown())
                .uptimePercentage(status.getUptimePercentage())
                .lastDowntimeAt(status.getLastDowntimeAt())
                .lastCheckedAt(status.getUpdatedAt())
                .build();
    }
}


package com.heartbeat.ping.service;

import com.heartbeat.ping.dto.analytics.MonitorStatusResponse;
import com.heartbeat.ping.modles.MonitorLogs;
import com.heartbeat.ping.modles.MonitorStatus;
import com.heartbeat.ping.repository.MonitorLogsRepository;
import com.heartbeat.ping.repository.MonitorRepository;
import com.heartbeat.ping.repository.MonitorStatusRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonitorAnalyticServiceTest {

    @Mock
    private MonitorRepository monitorRepository;

    @Mock
    private MonitorLogsRepository logsRepository;

    @Mock
    private MonitorStatusRepository monitorStatusRepository;

    @InjectMocks
    private MonitorAnalyticService service;

    @Test
    void statusUsesLatestLogForCurrentUpState() {
        UUID monitorId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        MonitorStatus status = MonitorStatus.builder()
                .totalChecks(1)
                .totalUp(0)
                .totalDown(1)
                .uptimePercentage(0.0)
                .updatedAt(LocalDateTime.now())
                .build();
        MonitorLogs latestLog = MonitorLogs.builder()
                .isUp(false)
                .checkedAt(LocalDateTime.now())
                .build();

        when(monitorRepository.existsByIdAndUser_Id(monitorId, userId)).thenReturn(true);
        when(monitorStatusRepository.findById(monitorId)).thenReturn(Optional.of(status));
        when(logsRepository.findTopByMonitor_IdOrderByCheckedAtDesc(monitorId)).thenReturn(Optional.of(latestLog));

        MonitorStatusResponse response = service.getMonitorStatus(monitorId, userId);

        assertFalse(response.isUp());
    }
}

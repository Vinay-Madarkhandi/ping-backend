package com.heartbeat.ping.service;

import com.heartbeat.ping.dto.analytics.MonitorStatusResponse;
import com.heartbeat.ping.modles.Monitor;
import com.heartbeat.ping.modles.MonitorLogs;
import com.heartbeat.ping.modles.MonitorState;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
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
                .currentState(MonitorState.DOWN)
                .updatedAt(LocalDateTime.now())
                .build();
        Monitor monitor = Monitor.builder().paused(false).build();

        when(monitorRepository.findByIdAndUser_Id(monitorId, userId)).thenReturn(Optional.of(monitor));
        when(monitorStatusRepository.findById(monitorId)).thenReturn(Optional.of(status));

        MonitorStatusResponse response = service.getMonitorStatus(monitorId, userId);

        assertFalse(response.isUp()); // currentState is DOWN
        assertEquals("DOWN", response.getDisplayState());
    }
}

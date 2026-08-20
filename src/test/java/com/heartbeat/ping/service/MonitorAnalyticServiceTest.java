package com.heartbeat.ping.service;

import com.heartbeat.ping.dto.analytics.MonitorStatusResponse;
import com.heartbeat.ping.modles.Monitor;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    private final UUID monitorId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    private MonitorStatusResponse statusFor(Monitor monitor, MonitorState state) {
        MonitorStatus status = MonitorStatus.builder()
                .totalChecks(1)
                .totalUp(0)
                .totalDown(1)
                .uptimePercentage(0.0)
                .currentState(state)
                .updatedAt(LocalDateTime.now())
                .build();

        when(monitorRepository.findByIdAndUser_Id(monitorId, userId)).thenReturn(Optional.of(monitor));
        when(monitorStatusRepository.findById(monitorId)).thenReturn(Optional.of(status));

        return service.getMonitorStatus(monitorId, userId);
    }

    @Test
    void derivesUpFromFsmStateNotTheLastRawLog() {
        Monitor monitor = Monitor.builder().paused(false).quotaBlocked(false).build();

        MonitorStatusResponse response = statusFor(monitor, MonitorState.DOWN);

        // isUp must follow the confirmed FSM state, so an INCONCLUSIVE probe can never flip it true.
        assertFalse(response.isUp());
        assertEquals("DOWN", response.getCurrentState());
        assertEquals("DOWN", response.getDisplayState());
        assertFalse(response.isQuotaBlocked());
    }

    @Test
    void displayStatePrefersQuotaBlockedOverPaused() {
        Monitor monitor = Monitor.builder().paused(true).quotaBlocked(true).build();

        MonitorStatusResponse response = statusFor(monitor, MonitorState.UP);

        // A system-imposed quota block outranks a user-imposed pause: the user cannot resume out of it.
        assertEquals("QUOTA_EXCEEDED", response.getDisplayState());
        assertTrue(response.isQuotaBlocked());
        // The underlying health state is still reported separately.
        assertEquals("UP", response.getCurrentState());
    }

    @Test
    void displayStateIsPausedWhenNotQuotaBlocked() {
        Monitor monitor = Monitor.builder().paused(true).quotaBlocked(false).build();

        MonitorStatusResponse response = statusFor(monitor, MonitorState.UP);

        assertEquals("PAUSED", response.getDisplayState());
        assertFalse(response.isQuotaBlocked());
    }

    @Test
    void displayStateFallsBackToHealthState() {
        Monitor monitor = Monitor.builder().paused(false).quotaBlocked(false).build();

        MonitorStatusResponse response = statusFor(monitor, MonitorState.UP);

        assertTrue(response.isUp());
        assertEquals("UP", response.getDisplayState());
    }
}

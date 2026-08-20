package com.heartbeat.ping.service;

import com.heartbeat.ping.dto.analytics.MonitorStatusResponse;
import com.heartbeat.ping.modles.Incident;
import com.heartbeat.ping.modles.Monitor;
import com.heartbeat.ping.modles.MonitorLogs;
import com.heartbeat.ping.modles.MonitorState;
import com.heartbeat.ping.modles.MonitorStatus;
import com.heartbeat.ping.modles.User;
import com.heartbeat.ping.repository.MonitorLogsRepository;
import com.heartbeat.ping.repository.MonitorRepository;
import com.heartbeat.ping.repository.MonitorStatusRepository;
import com.heartbeat.ping.service.incident.IncidentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonitorAnalyticServiceTest {

    @Mock
    private MonitorRepository monitorRepository;

    @Mock
    private MonitorLogsRepository logsRepository;

    @Mock
    private MonitorStatusRepository monitorStatusRepository;

    @Mock
    private IncidentService incidentService;

    @InjectMocks
    private MonitorAnalyticService service;

    private final UUID monitorId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    /** User's builder doesn't cover the inherited id field (plain @Builder, not @SuperBuilder). */
    private User userWithId(UUID id) {
        User user = User.builder().build();
        user.setId(id);
        return user;
    }

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

    @Test
    void exportLogsRejectsRangeOverNinetyDays() {
        User owner = userWithId(userId);
        Monitor monitor = Monitor.builder().user(owner).build();
        when(monitorRepository.findById(monitorId)).thenReturn(Optional.of(monitor));

        LocalDateTime from = LocalDateTime.now().minusDays(200);
        LocalDateTime to = LocalDateTime.now();

        assertThrows(IllegalArgumentException.class,
                () -> service.getMonitorLogsForExport(monitorId, userId, from, to));
    }

    @Test
    void exportLogsRejectsNonPositiveRange() {
        User owner = userWithId(userId);
        Monitor monitor = Monitor.builder().user(owner).build();
        when(monitorRepository.findById(monitorId)).thenReturn(Optional.of(monitor));

        LocalDateTime now = LocalDateTime.now();

        assertThrows(IllegalArgumentException.class,
                () -> service.getMonitorLogsForExport(monitorId, userId, now, now.minusHours(1)));
    }

    @Test
    void exportLogsDeniedForNonOwner() {
        User owner = userWithId(UUID.randomUUID());
        Monitor monitor = Monitor.builder().user(owner).build();
        when(monitorRepository.findById(monitorId)).thenReturn(Optional.of(monitor));

        LocalDateTime now = LocalDateTime.now();

        assertThrows(AccessDeniedException.class,
                () -> service.getMonitorLogsForExport(monitorId, userId, now.minusDays(1), now));
    }

    @Test
    void exportLogsReturnsLogsWithinRange() {
        User owner = userWithId(userId);
        Monitor monitor = Monitor.builder().user(owner).build();
        when(monitorRepository.findById(monitorId)).thenReturn(Optional.of(monitor));

        LocalDateTime now = LocalDateTime.now();
        List<MonitorLogs> logs = List.of(MonitorLogs.builder().statusCode(200).build());
        when(logsRepository.findByMonitorIdAndCheckedAtBetween(eq(monitorId), any(), any()))
                .thenReturn(logs);

        List<MonitorLogs> result = service.getMonitorLogsForExport(monitorId, userId, now.minusDays(1), now);

        assertEquals(1, result.size());
    }

    @Test
    void exportIncidentsDeniedWhenNotOwned() {
        when(monitorRepository.existsByIdAndUser_Id(monitorId, userId)).thenReturn(false);

        assertThrows(AccessDeniedException.class,
                () -> service.getIncidentsForExport(monitorId, userId));
    }

    @Test
    void exportIncidentsReturnsHistory() {
        when(monitorRepository.existsByIdAndUser_Id(monitorId, userId)).thenReturn(true);
        Page<Incident> page = new PageImpl<>(List.of(Incident.builder().build()));
        when(incidentService.history(eq(monitorId), any())).thenReturn(page);

        List<Incident> result = service.getIncidentsForExport(monitorId, userId);

        assertEquals(1, result.size());
    }
}

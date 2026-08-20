package com.heartbeat.ping.service;

import com.heartbeat.ping.dto.monitor.MonitorListResponseDto;
import com.heartbeat.ping.mapper.MonitorMapper;
import com.heartbeat.ping.modles.Monitor;
import com.heartbeat.ping.modles.MonitorMethod;
import com.heartbeat.ping.modles.MonitorState;
import com.heartbeat.ping.modles.MonitorStatus;
import com.heartbeat.ping.repository.MonitorPauseWindowRepository;
import com.heartbeat.ping.repository.MonitorRepository;
import com.heartbeat.ping.repository.MonitorStatusRepository;
import com.heartbeat.ping.repository.UserRepository;
import com.heartbeat.ping.service.incident.IncidentService;
import com.heartbeat.ping.service.security.UrlSafetyValidator;
import com.heartbeat.ping.service.time.DatabaseClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MonitorServiceTest {

    @Mock private MonitorRepository monitorRepository;
    @Mock private MonitorMapper monitorMapper;
    @Mock private UserRepository userRepository;
    @Mock private MonitorStatusRepository monitorStatusRepository;
    @Mock private MonitorPauseWindowRepository pauseWindowRepository;
    @Mock private IncidentService incidentService;
    @Mock private UrlSafetyValidator urlSafetyValidator;
    @Mock private UsageLimitService usageLimitService;
    @Mock private DatabaseClock clock;

    private MonitorService service;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new MonitorService(
                monitorRepository, monitorMapper, userRepository,
                monitorStatusRepository, pauseWindowRepository,
                incidentService, urlSafetyValidator, usageLimitService, clock
        );
    }

    // ---- getAllMonitorsByUserId: displayState precedence ----

    @Test
    void listMapping_quotaBlockedMonitor_displayStateIsQuotaExceeded() {
        Monitor monitor = buildMonitor(true, true, true);  // active, paused, quotaBlocked
        MonitorStatus status = buildStatus(monitor, MonitorState.UP, 99.0);

        when(monitorRepository.findByUser_IdAndDeletedAtIsNull(userId)).thenReturn(List.of(monitor));
        when(monitorStatusRepository.findAllById(List.of(monitor.getId()))).thenReturn(List.of(status));

        List<MonitorListResponseDto> result = service.getAllMonitorsByUserId(userId);

        assertThat(result).hasSize(1);
        MonitorListResponseDto dto = result.getFirst();
        // quotaBlocked takes highest precedence
        assertThat(dto.getDisplayState()).isEqualTo("QUOTA_EXCEEDED");
        assertThat(dto.getCurrentState()).isEqualTo("UP");
        assertThat(dto.isQuotaBlocked()).isTrue();
        assertThat(dto.isPaused()).isTrue();
        assertThat(dto.getUptimePercentage()).isEqualTo(99.0);
    }

    @Test
    void listMapping_pausedMonitor_displayStateIsPaused() {
        Monitor monitor = buildMonitor(true, true, false);  // active, paused, not quotaBlocked
        MonitorStatus status = buildStatus(monitor, MonitorState.DOWN, 85.0);

        when(monitorRepository.findByUser_IdAndDeletedAtIsNull(userId)).thenReturn(List.of(monitor));
        when(monitorStatusRepository.findAllById(List.of(monitor.getId()))).thenReturn(List.of(status));

        List<MonitorListResponseDto> result = service.getAllMonitorsByUserId(userId);

        MonitorListResponseDto dto = result.getFirst();
        assertThat(dto.getDisplayState()).isEqualTo("PAUSED");
        assertThat(dto.getCurrentState()).isEqualTo("DOWN");
    }

    @Test
    void listMapping_normalMonitor_displayStateMatchesCurrentState() {
        Monitor monitor = buildMonitor(true, false, false);
        MonitorStatus status = buildStatus(monitor, MonitorState.UP, 99.9);

        when(monitorRepository.findByUser_IdAndDeletedAtIsNull(userId)).thenReturn(List.of(monitor));
        when(monitorStatusRepository.findAllById(List.of(monitor.getId()))).thenReturn(List.of(status));

        List<MonitorListResponseDto> result = service.getAllMonitorsByUserId(userId);

        MonitorListResponseDto dto = result.getFirst();
        assertThat(dto.getDisplayState()).isEqualTo("UP");
        assertThat(dto.getCurrentState()).isEqualTo("UP");
        assertThat(dto.getIntervalMilliseconds()).isEqualTo(300_000);
        assertThat(dto.getTimeoutMilliseconds()).isEqualTo(5_000);
    }

    @Test
    void listMapping_noStatusRow_defaultsUptimeTo100AndStateToUnknown() {
        Monitor monitor = buildMonitor(true, false, false);

        when(monitorRepository.findByUser_IdAndDeletedAtIsNull(userId)).thenReturn(List.of(monitor));
        // Empty list — no status row exists
        when(monitorStatusRepository.findAllById(List.of(monitor.getId()))).thenReturn(List.of());

        List<MonitorListResponseDto> result = service.getAllMonitorsByUserId(userId);

        MonitorListResponseDto dto = result.getFirst();
        assertThat(dto.getUptimePercentage()).isEqualTo(100.0);
        assertThat(dto.getCurrentState()).isEqualTo("UNKNOWN");
        assertThat(dto.getDisplayState()).isEqualTo("UNKNOWN");
    }

    // ---- getMonitorById: happy path ----

    @Test
    void getMonitorById_happyPath_returnsEnrichedDto() {
        Monitor monitor = buildMonitor(true, false, false);
        MonitorStatus status = buildStatus(monitor, MonitorState.UP, 98.5);

        when(monitorRepository.findByIdAndUser_Id(monitor.getId(), userId)).thenReturn(Optional.of(monitor));
        when(monitorStatusRepository.findById(monitor.getId())).thenReturn(Optional.of(status));

        MonitorListResponseDto dto = service.getMonitorById(monitor.getId(), userId);

        assertThat(dto.getId()).isEqualTo(monitor.getId());
        assertThat(dto.getName()).isEqualTo("test-api");
        assertThat(dto.getUptimePercentage()).isEqualTo(98.5);
        assertThat(dto.getCurrentState()).isEqualTo("UP");
        assertThat(dto.getDisplayState()).isEqualTo("UP");
    }

    // ---- getMonitorById: access denied cases ----

    @Test
    void getMonitorById_monitorNotFound_throwsAccessDenied() {
        UUID monitorId = UUID.randomUUID();
        when(monitorRepository.findByIdAndUser_Id(monitorId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMonitorById(monitorId, userId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getMonitorById_archivedMonitor_throwsAccessDenied() {
        Monitor monitor = buildMonitor(false, false, false);
        monitor.setDeletedAt(Instant.now());  // archived

        when(monitorRepository.findByIdAndUser_Id(monitor.getId(), userId)).thenReturn(Optional.of(monitor));

        assertThatThrownBy(() -> service.getMonitorById(monitor.getId(), userId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getMonitorById_foreignUser_throwsAccessDenied() {
        UUID monitorId = UUID.randomUUID();
        UUID foreignUserId = UUID.randomUUID();
        when(monitorRepository.findByIdAndUser_Id(monitorId, foreignUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMonitorById(monitorId, foreignUserId))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ---- Helpers ----

    private Monitor buildMonitor(boolean active, boolean paused, boolean quotaBlocked) {
        Monitor monitor = Monitor.builder()
                .name("test-api")
                .url("https://example.com")
                .isActive(active)
                .paused(paused)
                .quotaBlocked(quotaBlocked)
                .monitorMethod(MonitorMethod.GET)
                .nextCheckAt(Instant.parse("2026-07-31T18:00:00Z"))
                .intervalMilliseconds(300_000)
                .timeoutMilliseconds(5_000)
                .build();
        monitor.setId(UUID.randomUUID());
        monitor.setCreatedAt(LocalDateTime.of(2026, 7, 1, 10, 0, 0));
        return monitor;
    }

    private MonitorStatus buildStatus(Monitor monitor, MonitorState state, double uptime) {
        MonitorStatus status = MonitorStatus.builder()
                .monitor(monitor)
                .currentState(state)
                .uptimePercentage(uptime)
                .totalChecks(100)
                .totalUp(state == MonitorState.UP ? 99 : 85)
                .totalDown(state == MonitorState.UP ? 1 : 15)
                .updatedAt(LocalDateTime.now())
                .build();
        // @MapsId only auto-sets monitorId during JPA persist; set it explicitly for unit tests.
        status.setMonitorId(monitor.getId());
        return status;
    }
}

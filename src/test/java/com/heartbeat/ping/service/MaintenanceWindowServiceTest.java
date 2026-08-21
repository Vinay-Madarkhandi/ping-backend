package com.heartbeat.ping.service;

import com.heartbeat.ping.dto.maintenance.MaintenanceWindowRequest;
import com.heartbeat.ping.dto.maintenance.MaintenanceWindowResponse;
import com.heartbeat.ping.modles.MaintenanceWindow;
import com.heartbeat.ping.modles.Monitor;
import com.heartbeat.ping.modles.MonitorPauseWindow;
import com.heartbeat.ping.repository.MaintenanceWindowRepository;
import com.heartbeat.ping.repository.MonitorPauseWindowRepository;
import com.heartbeat.ping.repository.MonitorRepository;
import com.heartbeat.ping.service.time.DatabaseClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MaintenanceWindowServiceTest {

    @Mock private MaintenanceWindowRepository windowRepository;
    @Mock private MonitorRepository monitorRepository;
    @Mock private MonitorPauseWindowRepository pauseWindowRepository;
    @Mock private DatabaseClock clock;

    private MaintenanceWindowService service;

    private final UUID userId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-08-20T10:00:00Z");

    @BeforeEach
    void setUp() {
        service = new MaintenanceWindowService(windowRepository, monitorRepository, pauseWindowRepository, clock);
        lenient().when(clock.now()).thenReturn(now);
    }

    private Monitor monitor(boolean paused) {
        Monitor m = Monitor.builder().name("api").paused(paused).build();
        m.setId(UUID.randomUUID());
        return m;
    }

    // ---- schedule ----

    @Test
    void schedule_rejectsWhenEndBeforeStart() {
        Monitor monitor = monitor(false);
        when(monitorRepository.findByIdAndUser_Id(monitor.getId(), userId)).thenReturn(Optional.of(monitor));

        MaintenanceWindowRequest request = MaintenanceWindowRequest.builder()
                .startsAt(now.plusSeconds(3600))
                .endsAt(now.plusSeconds(1800))
                .build();

        assertThatThrownBy(() -> service.schedule(monitor.getId(), userId, request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void schedule_rejectsWindowEndingInThePast() {
        Monitor monitor = monitor(false);
        when(monitorRepository.findByIdAndUser_Id(monitor.getId(), userId)).thenReturn(Optional.of(monitor));

        MaintenanceWindowRequest request = MaintenanceWindowRequest.builder()
                .startsAt(now.minusSeconds(7200))
                .endsAt(now.minusSeconds(3600))
                .build();

        assertThatThrownBy(() -> service.schedule(monitor.getId(), userId, request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void schedule_rejectsWindowLongerThan30Days() {
        Monitor monitor = monitor(false);
        when(monitorRepository.findByIdAndUser_Id(monitor.getId(), userId)).thenReturn(Optional.of(monitor));

        MaintenanceWindowRequest request = MaintenanceWindowRequest.builder()
                .startsAt(now)
                .endsAt(now.plusSeconds(31L * 24 * 3600))
                .build();

        assertThatThrownBy(() -> service.schedule(monitor.getId(), userId, request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void schedule_rejectsWhenPerMonitorCapReached() {
        Monitor monitor = monitor(false);
        when(monitorRepository.findByIdAndUser_Id(monitor.getId(), userId)).thenReturn(Optional.of(monitor));
        when(windowRepository.countByMonitor_IdAndEndsAtAfter(monitor.getId(), now)).thenReturn(20L);

        MaintenanceWindowRequest request = MaintenanceWindowRequest.builder()
                .startsAt(now.plusSeconds(3600))
                .endsAt(now.plusSeconds(7200))
                .build();

        assertThatThrownBy(() -> service.schedule(monitor.getId(), userId, request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void schedule_savesAWindowForAnOwnedMonitor() {
        Monitor monitor = monitor(false);
        when(monitorRepository.findByIdAndUser_Id(monitor.getId(), userId)).thenReturn(Optional.of(monitor));
        when(windowRepository.countByMonitor_IdAndEndsAtAfter(monitor.getId(), now)).thenReturn(0L);
        when(windowRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MaintenanceWindowRequest request = MaintenanceWindowRequest.builder()
                .title("Deploy")
                .startsAt(now.plusSeconds(3600))
                .endsAt(now.plusSeconds(7200))
                .build();

        MaintenanceWindowResponse response = service.schedule(monitor.getId(), userId, request);

        assertThat(response.getTitle()).isEqualTo("Deploy");
        assertThat(response.isActive()).isFalse();
        assertThat(response.isCompleted()).isFalse();
    }

    @Test
    void schedule_deniedForAMonitorNotOwnedByCaller() {
        UUID monitorId = UUID.randomUUID();
        when(monitorRepository.findByIdAndUser_Id(monitorId, userId)).thenReturn(Optional.empty());

        MaintenanceWindowRequest request = MaintenanceWindowRequest.builder()
                .startsAt(now.plusSeconds(3600))
                .endsAt(now.plusSeconds(7200))
                .build();

        assertThatThrownBy(() -> service.schedule(monitorId, userId, request))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ---- applyDueStarts / revertDueEnds ----

    @Test
    void applyDueStarts_pausesAnUnpausedMonitorAndOwnsTheResume() {
        Monitor monitor = monitor(false);
        MaintenanceWindow window = MaintenanceWindow.builder().monitor(monitor).startsAt(now).endsAt(now.plusSeconds(3600)).build();
        when(windowRepository.claimDueStarts(eq(now), any())).thenReturn(List.of(window));

        int count = service.applyDueStarts(50);

        assertThat(count).isEqualTo(1);
        assertThat(monitor.isPaused()).isTrue();
        assertThat(window.isPausedByWindow()).isTrue();
        assertThat(window.getAppliedAt()).isEqualTo(now);
        verify(pauseWindowRepository).save(any(MonitorPauseWindow.class));
    }

    @Test
    void applyDueStarts_leavesAnAlreadyPausedMonitorAloneAndDoesNotClaimOwnership() {
        Monitor monitor = monitor(true); // already paused for some other reason
        MaintenanceWindow window = MaintenanceWindow.builder().monitor(monitor).startsAt(now).endsAt(now.plusSeconds(3600)).build();
        when(windowRepository.claimDueStarts(eq(now), any())).thenReturn(List.of(window));

        service.applyDueStarts(50);

        assertThat(window.isPausedByWindow()).isFalse();
        verify(pauseWindowRepository, never()).save(any());
    }

    @Test
    void revertDueEnds_resumesOnlyWhenThisWindowOwnsThePause() {
        Monitor owned = monitor(true);
        MaintenanceWindow ownedWindow = MaintenanceWindow.builder().monitor(owned).pausedByWindow(true)
                .startsAt(now.minusSeconds(3600)).endsAt(now).build();

        Monitor notOwned = monitor(true); // paused by something else (e.g. user, or another window)
        MaintenanceWindow notOwnedWindow = MaintenanceWindow.builder().monitor(notOwned).pausedByWindow(false)
                .startsAt(now.minusSeconds(3600)).endsAt(now).build();

        when(windowRepository.claimDueEnds(eq(now), any())).thenReturn(List.of(ownedWindow, notOwnedWindow));
        when(pauseWindowRepository.findByOpenForMonitor(owned.getId())).thenReturn(Optional.empty());

        int count = service.revertDueEnds(50);

        assertThat(count).isEqualTo(2);
        assertThat(owned.isPaused()).isFalse(); // resumed — this window owned the pause
        assertThat(notOwned.isPaused()).isTrue(); // left alone — not this window's to resume
        assertThat(ownedWindow.getRevertedAt()).isEqualTo(now);
        assertThat(notOwnedWindow.getRevertedAt()).isEqualTo(now);
    }

    // ---- cancel ----

    @Test
    void cancel_deletesAWindowThatHasNotStartedYet() {
        Monitor monitor = monitor(false);
        UUID windowId = UUID.randomUUID();
        MaintenanceWindow window = MaintenanceWindow.builder().monitor(monitor).startsAt(now.plusSeconds(3600)).endsAt(now.plusSeconds(7200)).build();
        when(monitorRepository.findByIdAndUser_Id(monitor.getId(), userId)).thenReturn(Optional.of(monitor));
        when(windowRepository.findByIdAndMonitor_Id(windowId, monitor.getId())).thenReturn(Optional.of(window));

        service.cancel(monitor.getId(), userId, windowId);

        verify(windowRepository).delete(window);
    }

    @Test
    void cancel_resumesTheMonitorWhenEndingAnActiveWindowEarly() {
        Monitor monitor = monitor(true);
        UUID windowId = UUID.randomUUID();
        MaintenanceWindow window = MaintenanceWindow.builder().monitor(monitor).pausedByWindow(true)
                .appliedAt(now.minusSeconds(1800)).startsAt(now.minusSeconds(1800)).endsAt(now.plusSeconds(1800)).build();
        when(monitorRepository.findByIdAndUser_Id(monitor.getId(), userId)).thenReturn(Optional.of(monitor));
        when(windowRepository.findByIdAndMonitor_Id(windowId, monitor.getId())).thenReturn(Optional.of(window));
        when(pauseWindowRepository.findByOpenForMonitor(monitor.getId())).thenReturn(Optional.empty());

        service.cancel(monitor.getId(), userId, windowId);

        assertThat(monitor.isPaused()).isFalse();
        assertThat(window.getRevertedAt()).isEqualTo(now);
        verify(windowRepository, never()).delete(any());
    }

    @Test
    void cancel_rejectsAWindowThatAlreadyEnded() {
        Monitor monitor = monitor(false);
        UUID windowId = UUID.randomUUID();
        MaintenanceWindow window = MaintenanceWindow.builder().monitor(monitor)
                .appliedAt(now.minusSeconds(7200)).revertedAt(now.minusSeconds(3600))
                .startsAt(now.minusSeconds(7200)).endsAt(now.minusSeconds(3600)).build();
        when(monitorRepository.findByIdAndUser_Id(monitor.getId(), userId)).thenReturn(Optional.of(monitor));
        when(windowRepository.findByIdAndMonitor_Id(windowId, monitor.getId())).thenReturn(Optional.of(window));

        assertThatThrownBy(() -> service.cancel(monitor.getId(), userId, windowId))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

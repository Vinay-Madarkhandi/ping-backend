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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Schedules and enforces maintenance windows: pre-planned spans during which a monitor is
 * automatically paused (no checks, no alerts, excluded from uptime) without the user having to
 * click pause/resume at the exact start/end time.
 *
 * <p>{@link #applyDueStarts} and {@link #revertDueEnds} are called by
 * {@link com.heartbeat.ping.scheduler.MaintenanceWindowWorker} on a poll cadence; each claims its
 * batch with {@code FOR UPDATE SKIP LOCKED} (see {@link MaintenanceWindowRepository}), so this is
 * cluster-safe without a leader lock — the same approach the monitor check scheduler and outbox
 * workers use, chosen for the same reason: no single point of failure and no idle instances.
 */
@Service
@RequiredArgsConstructor
public class MaintenanceWindowService {

    /** Upcoming/active windows a single monitor may have at once — abuse/mistake guard, not a hard product limit. */
    private static final int MAX_WINDOWS_PER_MONITOR = 20;
    private static final Duration MAX_DURATION = Duration.ofDays(30);

    private final MaintenanceWindowRepository windowRepository;
    private final MonitorRepository monitorRepository;
    private final MonitorPauseWindowRepository pauseWindowRepository;
    private final DatabaseClock clock;

    @Transactional
    public MaintenanceWindowResponse schedule(UUID monitorId, UUID userId, MaintenanceWindowRequest request) {
        Monitor monitor = ownedMonitor(monitorId, userId);
        Instant now = clock.now();

        if (!request.getEndsAt().isAfter(request.getStartsAt())) {
            throw new IllegalArgumentException("endsAt must be after startsAt");
        }
        if (request.getEndsAt().isBefore(now)) {
            throw new IllegalArgumentException("endsAt must be in the future");
        }
        if (Duration.between(request.getStartsAt(), request.getEndsAt()).compareTo(MAX_DURATION) > 0) {
            throw new IllegalArgumentException("Maintenance windows can be at most 30 days long");
        }
        if (windowRepository.countByMonitor_IdAndEndsAtAfter(monitorId, now) >= MAX_WINDOWS_PER_MONITOR) {
            throw new IllegalArgumentException(
                    "This monitor already has " + MAX_WINDOWS_PER_MONITOR + " upcoming or active maintenance windows");
        }

        MaintenanceWindow window = MaintenanceWindow.builder()
                .monitor(monitor)
                .title(blankToNull(request.getTitle()))
                .startsAt(request.getStartsAt())
                .endsAt(request.getEndsAt())
                .createdAt(now)
                .build();

        return toResponse(windowRepository.save(window), now);
    }

    @Transactional(readOnly = true)
    public List<MaintenanceWindowResponse> list(UUID monitorId, UUID userId) {
        ownedMonitor(monitorId, userId);
        Instant now = clock.now();
        return windowRepository.findByMonitor_IdOrderByStartsAtDesc(monitorId).stream()
                .map(w -> toResponse(w, now))
                .toList();
    }

    /**
     * Cancels a window. One not yet started is simply removed (it never took effect). One
     * currently in effect is ended immediately — resuming the monitor right now if this window is
     * the one that paused it. One already ended cannot be cancelled again.
     */
    @Transactional
    public void cancel(UUID monitorId, UUID userId, UUID windowId) {
        ownedMonitor(monitorId, userId);
        MaintenanceWindow window = windowRepository.findByIdAndMonitor_Id(windowId, monitorId)
                .orElseThrow(() -> new ResourceNotFoundException("Maintenance window not found"));

        if (window.getRevertedAt() != null) {
            throw new IllegalArgumentException("This maintenance window has already ended");
        }

        if (window.getAppliedAt() == null) {
            windowRepository.delete(window);
            return;
        }

        Instant now = clock.now();
        if (window.isPausedByWindow()) {
            resumeMonitorForWindow(window.getMonitor(), now);
        }
        window.setRevertedAt(now);
    }

    /** Pauses every monitor whose maintenance window has just started. Called by the worker. */
    @Transactional
    public int applyDueStarts(int batchSize) {
        Instant now = clock.now();
        List<MaintenanceWindow> due = windowRepository.claimDueStarts(now, PageRequest.of(0, batchSize));

        for (MaintenanceWindow window : due) {
            Monitor monitor = window.getMonitor();
            boolean alreadyPaused = monitor.isPaused();
            if (!alreadyPaused) {
                monitor.setPaused(true);
                pauseWindowRepository.save(MonitorPauseWindow.builder()
                        .monitor(monitor)
                        .pausedAt(now)
                        .openForMonitor(monitor.getId())
                        .build());
            }
            window.setPausedByWindow(!alreadyPaused);
            window.setAppliedAt(now);
        }
        return due.size();
    }

    /** Resumes every monitor whose maintenance window has just ended (and that this window paused). */
    @Transactional
    public int revertDueEnds(int batchSize) {
        Instant now = clock.now();
        List<MaintenanceWindow> due = windowRepository.claimDueEnds(now, PageRequest.of(0, batchSize));

        for (MaintenanceWindow window : due) {
            if (window.isPausedByWindow()) {
                resumeMonitorForWindow(window.getMonitor(), now);
            }
            window.setRevertedAt(now);
        }
        return due.size();
    }

    /** Mirrors {@code MonitorService.resumeMonitor}'s mechanics, minus the ownership check (system-triggered). */
    private void resumeMonitorForWindow(Monitor monitor, Instant now) {
        monitor.setPaused(false);
        monitor.setNextCheckAt(now);
        pauseWindowRepository.findByOpenForMonitor(monitor.getId()).ifPresent(pauseWindow -> {
            pauseWindow.setResumedAt(now);
            pauseWindow.setOpenForMonitor(null);
        });
    }

    private MaintenanceWindowResponse toResponse(MaintenanceWindow window, Instant now) {
        boolean active = window.getAppliedAt() != null && window.getRevertedAt() == null;
        boolean completed = window.getRevertedAt() != null;
        return MaintenanceWindowResponse.builder()
                .id(window.getId())
                .title(window.getTitle())
                .startsAt(window.getStartsAt())
                .endsAt(window.getEndsAt())
                .active(active)
                .completed(completed)
                .createdAt(window.getCreatedAt())
                .build();
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private Monitor ownedMonitor(UUID monitorId, UUID userId) {
        return monitorRepository.findByIdAndUser_Id(monitorId, userId)
                .filter(m -> m.getDeletedAt() == null)
                .orElseThrow(() -> new AccessDeniedException("Monitor not found for this user"));
    }
}

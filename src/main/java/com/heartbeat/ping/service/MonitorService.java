package com.heartbeat.ping.service;

import com.heartbeat.ping.dto.monitor.CreateMonitorRequestDto;
import com.heartbeat.ping.dto.monitor.CreateMonitorResponseDto;
import com.heartbeat.ping.dto.monitor.EditMonitorRequest;
import com.heartbeat.ping.dto.monitor.MonitorListResponseDto;
import com.heartbeat.ping.dto.monitor.UpdateMonitorRequest;
import com.heartbeat.ping.mapper.MonitorMapper;
import com.heartbeat.ping.modles.Monitor;
import com.heartbeat.ping.modles.MonitorPauseWindow;
import com.heartbeat.ping.modles.MonitorState;
import com.heartbeat.ping.modles.MonitorStatus;
import com.heartbeat.ping.modles.User;
import com.heartbeat.ping.repository.MonitorPauseWindowRepository;
import com.heartbeat.ping.repository.MonitorRepository;
import com.heartbeat.ping.repository.MonitorStatusRepository;
import com.heartbeat.ping.repository.UserRepository;
import com.heartbeat.ping.service.incident.IncidentService;
import com.heartbeat.ping.service.security.UrlSafetyValidator;
import com.heartbeat.ping.service.time.DatabaseClock;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MonitorService {

    private final MonitorRepository monitorRepository;
    private final MonitorMapper monitorMapper;
    private final UserRepository userRepository;
    private final MonitorStatusRepository monitorStatusRepository;
    private final MonitorPauseWindowRepository pauseWindowRepository;
    private final IncidentService incidentService;
    private final UrlSafetyValidator urlSafetyValidator;
    private final UsageLimitService usageLimitService;
    private final DatabaseClock clock;

    public List<MonitorListResponseDto> getAllMonitorsByUserId(UUID userId) {
        return toDtos(monitorRepository.findByUser_IdAndDeletedAtIsNull(userId));
    }

    /**
     * Archived (soft-deleted) monitors. Kept queryable so archiving is reversible rather than looking
     * like data loss — the rows, logs and incidents were never deleted.
     */
    public List<MonitorListResponseDto> getArchivedMonitorsByUserId(UUID userId) {
        return toDtos(monitorRepository.findByUser_IdAndDeletedAtIsNotNull(userId));
    }

    private List<MonitorListResponseDto> toDtos(List<Monitor> monitors) {
        // Batch-fetch all status rows in one query to eliminate the N+1
        List<UUID> monitorIds = monitors.stream().map(Monitor::getId).toList();
        Map<UUID, MonitorStatus> statusMap = monitorStatusRepository.findAllById(monitorIds)
                .stream()
                .collect(Collectors.toMap(MonitorStatus::getMonitorId, Function.identity()));

        return monitors.stream()
                .map(m -> toDto(m, statusMap.get(m.getId())))
                .toList();
    }

    /**
     * Returns the enriched DTO for a single monitor owned by the caller.
     * Throws AccessDeniedException when not found, not owned, or archived — so existence of
     * other users' monitors is never leaked (GlobalExceptionHandler maps to 403).
     */
    public MonitorListResponseDto getMonitorById(UUID monitorId, UUID userId) {
        Monitor monitor = monitorRepository.findByIdAndUser_Id(monitorId, userId)
                .filter(m -> m.getDeletedAt() == null)
                .orElseThrow(() -> new AccessDeniedException("Monitor not found for this user"));

        MonitorStatus status = monitorStatusRepository.findById(monitorId).orElse(null);
        return toDto(monitor, status);
    }

    /**
     * Replaces a monitor's configuration.
     *
     * <p>Two safety re-checks run on every edit, not just on creation: the URL goes back through the
     * SSRF guard (an edit must not be a way to retarget a monitor at internal infrastructure), and the
     * interval/timeout go back through the plan limits (a user downgraded from PRO to FREE must not
     * keep a sub-minimum interval by editing).
     *
     * <p>When the URL changes the monitor is now watching a different target, so carrying the old
     * target's failure state forward would be wrong: any open incident is resolved and the alert FSM
     * is reset to UNKNOWN. Lifetime check totals are kept — they are history, not current state.
     */
    @Transactional
    public MonitorListResponseDto editMonitor(UUID monitorId, UUID userId, EditMonitorRequest request) {
        Monitor monitor = monitorRepository.findByIdAndUser_Id(monitorId, userId)
                .filter(m -> m.getDeletedAt() == null)
                .orElseThrow(() -> new AccessDeniedException("Monitor not found for this user"));

        String newUrl = request.getUrl().trim();
        boolean urlChanged = !newUrl.equals(monitor.getUrl());
        if (urlChanged) {
            urlSafetyValidator.validate(newUrl);
        }
        usageLimitService.validateMonitorSettings(
                monitor.getUser(), request.getIntervalMilliseconds(), request.getTimeoutMilliseconds());

        monitor.setName(request.getName().trim());
        monitor.setUrl(newUrl);
        monitor.setIntervalMilliseconds(request.getIntervalMilliseconds());
        monitor.setTimeoutMilliseconds(request.getTimeoutMilliseconds());
        monitor.setMonitorMethod(monitorMapper.parseMethod(request.getMonitorMethod()));
        monitor.setExpectedStatusCode(request.getExpectedStatusCode());
        monitor.setKeyword(blankToNull(request.getKeyword()));
        monitor.setFollowRedirects(request.getFollowRedirects() == null || request.getFollowRedirects());
        monitor.setCustomHeaders(request.getCustomHeaders());
        if (request.getActive() != null) {
            monitor.setActive(request.getActive());
        }

        Instant now = clock.now();
        if (urlChanged) {
            resetHealthState(monitor, now);
        }
        // Apply the new configuration on the next poll rather than waiting out the old interval.
        monitor.setNextCheckAt(now);

        MonitorStatus status = monitorStatusRepository.findById(monitorId).orElse(null);
        return toDto(monitor, status);
    }

    /** Clears the alert FSM after a retarget so the previous URL's failures cannot trigger an alert. */
    private void resetHealthState(Monitor monitor, Instant now) {
        incidentService.resolve(monitor.getId(), now);
        monitorStatusRepository.findById(monitor.getId()).ifPresent(status -> {
            status.setCurrentState(MonitorState.UNKNOWN);
            status.setConsecutiveFailures(0);
            status.setConsecutiveSuccesses(0);
            status.setDownSince(null);
        });
    }

    /**
     * Restores an archived monitor. Treated as a creation for limit purposes, since archived monitors
     * do not count against the plan — otherwise restoring would silently exceed the cap.
     */
    @Transactional
    public MonitorListResponseDto restoreMonitor(UUID monitorId, UUID userId) {
        Monitor monitor = monitorRepository.findByIdAndUser_Id(monitorId, userId)
                .orElseThrow(() -> new AccessDeniedException("Monitor not found for this user"));

        if (monitor.getDeletedAt() == null) {
            return toDto(monitor, monitorStatusRepository.findById(monitorId).orElse(null)); // already active
        }

        usageLimitService.validateRestore(monitor.getUser());

        monitor.setDeletedAt(null);
        monitor.setActive(true);
        monitor.setNextCheckAt(clock.now());

        return toDto(monitor, monitorStatusRepository.findById(monitorId).orElse(null));
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    /**
     * Soft-deletes (archives) a monitor: it stops being scheduled but its logs and incident
     * history are preserved. Any open incident is resolved so the outage record closes cleanly.
     */
    @Transactional
    public void deleteMonitor(UUID monitorId, UUID userId) {
        Monitor monitor = monitorRepository
                .findByIdAndUser_Id(monitorId, userId)
                .orElseThrow(() ->
                        new AccessDeniedException("Monitor not found or does not belong to user")
                );

        if (monitor.getDeletedAt() != null) {
            return; // already archived
        }

        Instant now = clock.now();
        monitor.setDeletedAt(now);
        monitor.setActive(false);
        incidentService.resolve(monitorId, now);
    }

    @Transactional
    public void updateMonitor(UUID monitorId, UpdateMonitorRequest updateMonitorRequest, UUID userId) {
        Monitor monitor = monitorRepository
                .findByIdAndUser_Id(monitorId, userId)
                .orElseThrow(() -> new AccessDeniedException("Monitor not found or does not belong to user"));

        if(updateMonitorRequest.getActive() != null){
            monitor.setActive(updateMonitorRequest.getActive());

            if (updateMonitorRequest.getActive()) {
                monitor.setNextCheckAt(Instant.now());
            }
        }
    }


    /** Pauses a monitor and opens a pause window so the paused time is excluded from uptime. */
    @Transactional
    public void pauseMonitor(UUID monitorId, UUID userId) {
        Monitor monitor = ownedMonitor(monitorId, userId);
        if (monitor.isPaused()) {
            return;
        }
        Instant now = clock.now();
        monitor.setPaused(true);
        pauseWindowRepository.save(MonitorPauseWindow.builder()
                .monitor(monitor)
                .pausedAt(now)
                .openForMonitor(monitorId)
                .build());
    }

    /** Resumes a monitor, closes its open pause window and schedules it to be checked promptly. */
    @Transactional
    public void resumeMonitor(UUID monitorId, UUID userId) {
        Monitor monitor = ownedMonitor(monitorId, userId);
        if (!monitor.isPaused()) {
            return;
        }
        Instant now = clock.now();
        monitor.setPaused(false);
        monitor.setNextCheckAt(now);
        pauseWindowRepository.findByOpenForMonitor(monitorId).ifPresent(window -> {
            window.setResumedAt(now);
            window.setOpenForMonitor(null);
        });
    }

    private Monitor ownedMonitor(UUID monitorId, UUID userId) {
        return monitorRepository.findByIdAndUser_Id(monitorId, userId)
                .orElseThrow(() -> new AccessDeniedException("Monitor not found or does not belong to user"));
    }

    public CreateMonitorResponseDto monitorUrl(CreateMonitorRequestDto createMonitorRequestDto){

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assert authentication != null;
        String email = authentication.getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        urlSafetyValidator.validate(createMonitorRequestDto.getUrl());
        usageLimitService.validateNewMonitor(
                user,
                createMonitorRequestDto.getIntervalMilliseconds(),
                createMonitorRequestDto.getTimeoutMilliseconds());

        Monitor monitor = monitorMapper.toEntity(createMonitorRequestDto);
        monitor.setUser(user);
        Monitor response = monitorRepository.save(monitor);

        MonitorStatus status = MonitorStatus.builder()
                .monitor(response)
                .totalChecks(0)
                .totalUp(0)
                .totalDown(0)
                .uptimePercentage(100.0)
                .updatedAt(LocalDateTime.now())
                .build();
        monitorStatusRepository.save(status);

        return monitorMapper.toResponse(response);
    }

    // ---- Private helpers ----

    /**
     * Maps a Monitor entity + its optional MonitorStatus row into the enriched list DTO.
     * displayState precedence: quotaBlocked → "QUOTA_EXCEEDED"; paused → "PAUSED"; else FSM state.
     */
    private MonitorListResponseDto toDto(Monitor monitor, MonitorStatus status) {
        double uptime = (status != null) ? status.getUptimePercentage() : 100.0;
        // Defensive: a status row can exist before the FSM has ever run, so never dereference blindly.
        String currentState = (status != null && status.getCurrentState() != null)
                ? status.getCurrentState().name()
                : MonitorState.UNKNOWN.name();

        String displayState;
        if (monitor.isQuotaBlocked()) {
            displayState = "QUOTA_EXCEEDED";
        } else if (monitor.isPaused()) {
            displayState = "PAUSED";
        } else {
            displayState = currentState;
        }

        return MonitorListResponseDto.builder()
                .id(monitor.getId())
                .name(monitor.getName())
                .url(monitor.getUrl())
                .active(monitor.isActive())
                .method(monitor.getMonitorMethod())
                .nextCheckAt(monitor.getNextCheckAt())
                .uptimePercentage(uptime)
                .createdAt(monitor.getCreatedAt())
                .paused(monitor.isPaused())
                .quotaBlocked(monitor.isQuotaBlocked())
                .currentState(currentState)
                .displayState(displayState)
                .intervalMilliseconds(monitor.getIntervalMilliseconds())
                .timeoutMilliseconds(monitor.getTimeoutMilliseconds())
                .build();
    }
}

package com.heartbeat.ping.service;

import com.heartbeat.ping.dto.monitor.CreateMonitorRequestDto;
import com.heartbeat.ping.dto.monitor.CreateMonitorResponseDto;
import com.heartbeat.ping.dto.monitor.MonitorListResponseDto;
import com.heartbeat.ping.dto.monitor.UpdateMonitorRequest;
import com.heartbeat.ping.mapper.MonitorMapper;
import com.heartbeat.ping.modles.Monitor;
import com.heartbeat.ping.modles.MonitorPauseWindow;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

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

        List<Monitor> monitors = monitorRepository.findByUser_IdAndDeletedAtIsNull(userId);

        return monitors.stream()
                .map(m -> {
                    double uptime = monitorStatusRepository
                            .findById(m.getId())
                            .map(MonitorStatus::getUptimePercentage)
                            .orElse(100.0); // default if no checks yet

                    return MonitorListResponseDto.builder()
                            .id(m.getId())
                            .name(m.getName())
                            .url(m.getUrl())
                            .active(m.isActive())
                            .method(m.getMonitorMethod())
                            .nextCheckAt(m.getNextCheckAt())
                            .uptimePercentage(uptime)
                            .build();
                })
                .toList();
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
                        new RuntimeException("Monitor not found or does not belong to user")
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
                .orElseThrow(() -> new RuntimeException("Monitor not found"));

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
                .orElseThrow(() -> new RuntimeException("Monitor not found or does not belong to user"));
    }

    public CreateMonitorResponseDto monitorUrl(CreateMonitorRequestDto createMonitorRequestDto){

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assert authentication != null;
        String email = authentication.getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

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
}

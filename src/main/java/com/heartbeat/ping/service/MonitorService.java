package com.heartbeat.ping.service;

import com.heartbeat.ping.dto.monitor.CreateMonitorRequestDto;
import com.heartbeat.ping.dto.monitor.CreateMonitorResponseDto;
import com.heartbeat.ping.dto.monitor.MonitorListResponseDto;
import com.heartbeat.ping.dto.monitor.UpdateMonitorRequest;
import com.heartbeat.ping.mapper.MonitorMapper;
import com.heartbeat.ping.modles.Monitor;
import com.heartbeat.ping.modles.MonitorMethod;
import com.heartbeat.ping.modles.MonitorStatus;
import com.heartbeat.ping.modles.User;
import com.heartbeat.ping.repository.MonitorLogsRepository;
import com.heartbeat.ping.repository.MonitorRepository;
import com.heartbeat.ping.repository.MonitorStatusRepository;
import com.heartbeat.ping.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class MonitorService {

    private final MonitorRepository monitorRepository;
    private final MonitorMapper monitorMapper;
    private final UserRepository userRepository;
    private final MonitorLogsRepository monitorLogsRepository;
    private final MonitorStatusRepository monitorStatusRepository;

    public MonitorService(MonitorRepository monitorRepository, MonitorMapper monitorMapper, UserRepository userRepository, MonitorLogsRepository monitorLogsRepository, MonitorStatusRepository monitorStatusRepository){
        this.monitorRepository = monitorRepository;
        this.monitorMapper = monitorMapper;
        this.userRepository = userRepository;
        this.monitorLogsRepository = monitorLogsRepository;
        this.monitorStatusRepository = monitorStatusRepository;
    }

    public List<MonitorListResponseDto> getAllMonitorsByUserId(UUID userId) {

        List<Monitor> monitors = monitorRepository.findByUser_Id(userId);

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

    public void deleteMonitor(UUID monitorId, UUID userId) {
        Monitor monitor = monitorRepository
                .findByIdAndUser_Id((monitorId), userId)
                .orElseThrow(() ->
                        new RuntimeException("Monitor not found or does not belong to user")
                );

        // Optional but recommended
        monitorStatusRepository.deleteById(monitorId);

        monitorRepository.delete(monitor);
    }

    @Transactional
    public void updateMonitor(UUID monitorId, UpdateMonitorRequest updateMonitorRequest, UUID userId) {
        Monitor monitor = monitorRepository
                .findByIdAndUser_Id(monitorId, userId)
                .orElseThrow(() -> new RuntimeException("Monitor not found"));

        if(updateMonitorRequest.getActive() != null){
            monitor.setActive(updateMonitorRequest.getActive());

            if (updateMonitorRequest.getActive()) {
                monitor.setNextCheckAt(LocalDateTime.now());
            }
        }
    }


    public CreateMonitorResponseDto monitorUrl(CreateMonitorRequestDto createMonitorRequestDto){

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assert authentication != null;
        String email = authentication.getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));


        Monitor monitor = monitorMapper.toEntity(createMonitorRequestDto);
        monitor.setUser(user);
        Monitor response = monitorRepository.save(monitor);

        return monitorMapper.toResponse(response);
    }
}

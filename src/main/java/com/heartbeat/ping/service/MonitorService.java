package com.heartbeat.ping.service;

import com.heartbeat.ping.dto.monitor.CreateMonitorRequestDto;
import com.heartbeat.ping.dto.monitor.CreateMonitorResponseDto;
import com.heartbeat.ping.mapper.MonitorMapper;
import com.heartbeat.ping.modles.Monitor;
import com.heartbeat.ping.modles.MonitorLogs;
import com.heartbeat.ping.modles.User;
import com.heartbeat.ping.repository.MonitorLogsRepository;
import com.heartbeat.ping.repository.MonitorRepository;
import com.heartbeat.ping.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MonitorService {

    private final MonitorRepository monitorRepository;
    private final MonitorMapper monitorMapper;
    private final UserRepository userRepository;
    private final MonitorLogsRepository monitorLogsRepository;

    public MonitorService(MonitorRepository monitorRepository, MonitorMapper monitorMapper, UserRepository userRepository, MonitorLogsRepository monitorLogsRepository){
        this.monitorRepository = monitorRepository;
        this.monitorMapper = monitorMapper;
        this.userRepository = userRepository;
        this.monitorLogsRepository = monitorLogsRepository;
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

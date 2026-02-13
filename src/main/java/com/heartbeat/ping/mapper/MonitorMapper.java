package com.heartbeat.ping.mapper;

import com.heartbeat.ping.dto.monitor.CreateMonitorRequestDto;
import com.heartbeat.ping.dto.monitor.CreateMonitorResponseDto;
import com.heartbeat.ping.modles.Monitor;
import com.heartbeat.ping.modles.MonitorMethod;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class MonitorMapper {
    public Monitor toEntity(CreateMonitorRequestDto monitorRequestDto){
        return Monitor.builder()
                .name(monitorRequestDto.getName())
                .url(monitorRequestDto.getUrl())
                .intervalMilliseconds(monitorRequestDto.getIntervalMilliseconds())
                .timeoutMilliseconds(monitorRequestDto.getTimeoutMilliseconds())
                .isActive(true)
                .monitorMethod(
                        MonitorMethod.valueOf(monitorRequestDto.getMonitorMethod())
                )
                .nextCheckAt(LocalDateTime.now())
                .build();
    }

    public CreateMonitorResponseDto toResponse(Monitor monitor){
        return CreateMonitorResponseDto.builder()
                .id(monitor.getId().toString())
                .name(monitor.getName())
                .url(monitor.getUrl())
                .isActive(monitor.isActive())
                .createdAt(monitor.getCreatedAt())
                .build();
    }
}

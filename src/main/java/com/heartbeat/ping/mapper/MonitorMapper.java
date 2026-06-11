package com.heartbeat.ping.mapper;

import com.heartbeat.ping.dto.monitor.CreateMonitorRequestDto;
import com.heartbeat.ping.dto.monitor.CreateMonitorResponseDto;
import com.heartbeat.ping.modles.Monitor;
import com.heartbeat.ping.modles.MonitorMethod;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Locale;

@Component
public class MonitorMapper {
    public Monitor toEntity(CreateMonitorRequestDto monitorRequestDto){
        if (monitorRequestDto.getIntervalMilliseconds() <= 0) {
            throw new IllegalArgumentException("intervalMilliseconds must be greater than 0");
        }

        if (monitorRequestDto.getTimeoutMilliseconds() <= 0) {
            throw new IllegalArgumentException("timeoutMilliseconds must be greater than 0");
        }

        return Monitor.builder()
                .name(monitorRequestDto.getName())
                .url(monitorRequestDto.getUrl())
                .intervalMilliseconds(monitorRequestDto.getIntervalMilliseconds())
                .timeoutMilliseconds(monitorRequestDto.getTimeoutMilliseconds())
                .isActive(true)
                .monitorMethod(parseMethod(monitorRequestDto.getMonitorMethod()))
                .nextCheckAt(LocalDateTime.now())
                .build();
    }

    private MonitorMethod parseMethod(String method) {
        if (method == null || method.isBlank()) {
            return MonitorMethod.GET;
        }

        try {
            return MonitorMethod.valueOf(method.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("monitorMethod must be GET or POST");
        }
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

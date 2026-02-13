package com.heartbeat.ping.controller;

import com.heartbeat.ping.dto.monitor.CreateMonitorRequestDto;
import com.heartbeat.ping.dto.monitor.CreateMonitorResponseDto;
import com.heartbeat.ping.modles.Monitor;
import com.heartbeat.ping.service.MonitorService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/monitors")
public class MonitorController {

    private final MonitorService monitorService;

    public MonitorController(MonitorService monitorService) {
        this.monitorService = monitorService;
    }

    @PostMapping
    public ResponseEntity<CreateMonitorResponseDto> createMonitor(@RequestBody CreateMonitorRequestDto createMonitorRequestDto){
        CreateMonitorResponseDto responseDto = monitorService.monitorUrl(createMonitorRequestDto);

        return new ResponseEntity<>(responseDto, HttpStatus.CREATED);
    }
}

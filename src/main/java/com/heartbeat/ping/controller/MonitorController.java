package com.heartbeat.ping.controller;

import com.heartbeat.ping.dto.analytics.MonitorLogResponseDto;
import com.heartbeat.ping.dto.analytics.MonitorStatusResponse;
import com.heartbeat.ping.dto.monitor.CreateMonitorRequestDto;
import com.heartbeat.ping.dto.monitor.CreateMonitorResponseDto;
import com.heartbeat.ping.modles.MonitorLogs;
import com.heartbeat.ping.repository.UserRepository;
import com.heartbeat.ping.service.MonitorAnalyticService;
import com.heartbeat.ping.service.MonitorService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/monitors")
public class MonitorController {

    private final MonitorService monitorService;
    private final MonitorAnalyticService monitorAnalyticService;
    private final UserRepository userRepository;

    public MonitorController(MonitorService monitorService, MonitorAnalyticService monitorAnalyticService, UserRepository userRepository) {
        this.monitorService = monitorService;
        this.monitorAnalyticService = monitorAnalyticService;
        this.userRepository = userRepository;
    }

    @PostMapping
    public ResponseEntity<CreateMonitorResponseDto> createMonitor(@RequestBody CreateMonitorRequestDto createMonitorRequestDto){
        CreateMonitorResponseDto responseDto = monitorService.monitorUrl(createMonitorRequestDto);

        return new ResponseEntity<>(responseDto, HttpStatus.CREATED);
    }

    @GetMapping("/{monitorId}/logs")
    public ResponseEntity<Page<MonitorLogResponseDto>> getLogs(
            @PathVariable UUID monitorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication
        ) {
        String email = authentication.getName();
        UUID userId = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"))
                .getId();

        Page<MonitorLogs> logs = monitorAnalyticService.getMonitorLogs(
                monitorId,
                userId,
                PageRequest.of(page, size)
        );

        return ResponseEntity.ok(
                logs.map(MonitorLogResponseDto::from)
        );
    }

    @GetMapping("/{monitorId}/status")
    public ResponseEntity<MonitorStatusResponse> getStatus(
            @PathVariable UUID monitorId,
            Authentication authentication
    ){

        String email = authentication.getName();
        UUID userId = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"))
                .getId();

        MonitorStatusResponse statusResponse = monitorAnalyticService.getMonitorStatus(monitorId, userId);

        return new ResponseEntity<>(statusResponse, HttpStatus.OK);
    }
}

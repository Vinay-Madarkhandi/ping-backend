package com.heartbeat.ping.controller;

import com.heartbeat.ping.dto.monitor.HeartbeatPingResponse;
import com.heartbeat.ping.service.execution.HeartbeatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Unauthenticated (permitAll — see SpringSecurity's {@code /api/v1/public/**} rule) endpoint an
 * external job pings to report a HEARTBEAT monitor as alive. GET and POST both work — cron jobs,
 * curl one-liners, and job schedulers vary in which they use most conveniently; HEAD is handled
 * automatically by Spring MVC's default {@code @GetMapping} behavior.
 */
@RestController
@RequestMapping("/api/v1/public/heartbeat")
@RequiredArgsConstructor
public class HeartbeatController {

    private final HeartbeatService heartbeatService;

    @GetMapping("/{token}")
    public ResponseEntity<HeartbeatPingResponse> pingGet(@PathVariable String token) {
        return ResponseEntity.ok(heartbeatService.recordPing(token));
    }

    @PostMapping("/{token}")
    public ResponseEntity<HeartbeatPingResponse> pingPost(@PathVariable String token) {
        return ResponseEntity.ok(heartbeatService.recordPing(token));
    }
}

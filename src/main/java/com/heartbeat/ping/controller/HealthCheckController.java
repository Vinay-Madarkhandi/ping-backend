package com.heartbeat.ping.controller;

import com.heartbeat.ping.dto.monitor.HealthMonitorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
public class HealthCheckController {
    @GetMapping
    public ResponseEntity<?> getHealthCheck(){
        return ResponseEntity.ok(new HealthMonitorResponse("up"));
    }
}

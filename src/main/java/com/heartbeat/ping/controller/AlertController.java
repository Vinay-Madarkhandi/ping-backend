package com.heartbeat.ping.controller;

import com.heartbeat.ping.dto.alerts.AlertResponse;
import com.heartbeat.ping.repository.UserRepository;
import com.heartbeat.ping.service.notification.AlertHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Account-wide alert delivery history. The per-monitor view lives on
 * {@code /api/v1/monitors/{id}/alerts}; this is the cross-monitor feed, which is what an operator
 * actually wants when asking "did anything fail to notify?".
 */
@RestController
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertHistoryService alertHistoryService;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<Page<AlertResponse>> alerts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication
    ) {
        UUID userId = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("User not found"))
                .getId();
        return ResponseEntity.ok(alertHistoryService.forUser(userId, PageRequest.of(page, size)));
    }
}

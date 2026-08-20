package com.heartbeat.ping.controller;

import com.heartbeat.ping.dto.usage.UsageResponse;
import com.heartbeat.ping.service.UsageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in user's own usage against their plan. Authenticated by the default
 * {@code anyRequest().authenticated()} rule, and always scoped to the caller — unlike
 * {@code /api/v1/admin/usage}, there is no way to read another user's numbers here.
 */
@RestController
@RequestMapping("/api/v1/usage")
@RequiredArgsConstructor
public class UsageController {

    private final UsageService usageService;

    @GetMapping
    public ResponseEntity<UsageResponse> usage(Authentication authentication) {
        return ResponseEntity.ok(usageService.usageFor(authentication.getName()));
    }
}

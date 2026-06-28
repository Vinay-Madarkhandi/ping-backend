package com.heartbeat.ping.controller;

import com.heartbeat.ping.config.properties.AdminProperties;
import com.heartbeat.ping.dto.admin.AdminUsageDto;
import com.heartbeat.ping.service.AdminUsageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Operational visibility into per-user resource consumption. No role system exists, so access is
 * restricted to the configured admin email allowlist ({@code monitor.admin.emails}).
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminUsageService adminUsageService;
    private final AdminProperties adminProperties;

    @GetMapping("/usage")
    public ResponseEntity<List<AdminUsageDto>> usage(Authentication authentication) {
        requireAdmin(authentication);
        return ResponseEntity.ok(adminUsageService.usage());
    }

    private void requireAdmin(Authentication authentication) {
        if (authentication == null || !adminProperties.getEmails().contains(authentication.getName())) {
            throw new AccessDeniedException("Admin access required");
        }
    }
}

package com.heartbeat.ping.controller;

import com.heartbeat.ping.dto.maintenance.MaintenanceWindowRequest;
import com.heartbeat.ping.dto.maintenance.MaintenanceWindowResponse;
import com.heartbeat.ping.repository.UserRepository;
import com.heartbeat.ping.service.MaintenanceWindowService;
import com.heartbeat.ping.service.ResourceNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/monitors/{monitorId}/maintenance-windows")
@RequiredArgsConstructor
public class MaintenanceWindowController {

    private final MaintenanceWindowService maintenanceWindowService;
    private final UserRepository userRepository;

    @PostMapping
    public ResponseEntity<MaintenanceWindowResponse> schedule(
            @PathVariable UUID monitorId,
            @Valid @RequestBody MaintenanceWindowRequest request,
            Authentication authentication
    ) {
        MaintenanceWindowResponse response =
                maintenanceWindowService.schedule(monitorId, currentUserId(authentication), request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<MaintenanceWindowResponse>> list(
            @PathVariable UUID monitorId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(maintenanceWindowService.list(monitorId, currentUserId(authentication)));
    }

    @DeleteMapping("/{windowId}")
    public ResponseEntity<Void> cancel(
            @PathVariable UUID monitorId,
            @PathVariable UUID windowId,
            Authentication authentication
    ) {
        maintenanceWindowService.cancel(monitorId, currentUserId(authentication), windowId);
        return ResponseEntity.noContent().build();
    }

    private UUID currentUserId(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"))
                .getId();
    }
}

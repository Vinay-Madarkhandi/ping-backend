package com.heartbeat.ping.controller;

import com.heartbeat.ping.dto.alertchannel.AlertChannelRequest;
import com.heartbeat.ping.dto.alertchannel.AlertChannelResponse;
import com.heartbeat.ping.dto.alertchannel.MonitorAlertChannelsRequest;
import com.heartbeat.ping.repository.UserRepository;
import com.heartbeat.ping.service.ResourceNotFoundException;
import com.heartbeat.ping.service.notification.AlertChannelService;
import com.heartbeat.ping.service.notification.AlertChannelService.AlertChannelTestResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AlertChannelController {

    private final AlertChannelService alertChannelService;
    private final UserRepository userRepository;

    @PostMapping("/api/v1/alert-channels")
    public ResponseEntity<AlertChannelResponse> create(
            @Valid @RequestBody AlertChannelRequest request,
            Authentication authentication
    ) {
        return new ResponseEntity<>(
                alertChannelService.create(currentUserId(authentication), request), HttpStatus.CREATED);
    }

    @GetMapping("/api/v1/alert-channels")
    public ResponseEntity<List<AlertChannelResponse>> list(Authentication authentication) {
        return ResponseEntity.ok(alertChannelService.list(currentUserId(authentication)));
    }

    @DeleteMapping("/api/v1/alert-channels/{channelId}")
    public ResponseEntity<Void> delete(@PathVariable UUID channelId, Authentication authentication) {
        alertChannelService.delete(channelId, currentUserId(authentication));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/alert-channels/{channelId}/test")
    public ResponseEntity<AlertChannelTestResult> test(@PathVariable UUID channelId, Authentication authentication) {
        return ResponseEntity.ok(alertChannelService.test(channelId, currentUserId(authentication)));
    }

    @GetMapping("/api/v1/monitors/{monitorId}/alert-channels")
    public ResponseEntity<List<UUID>> getMonitorChannels(
            @PathVariable UUID monitorId, Authentication authentication
    ) {
        return ResponseEntity.ok(alertChannelService.getMonitorChannelIds(monitorId, currentUserId(authentication)));
    }

    @PutMapping("/api/v1/monitors/{monitorId}/alert-channels")
    public ResponseEntity<Void> setMonitorChannels(
            @PathVariable UUID monitorId,
            @RequestBody MonitorAlertChannelsRequest request,
            Authentication authentication
    ) {
        alertChannelService.setMonitorChannels(monitorId, currentUserId(authentication), request.getChannelIds());
        return ResponseEntity.noContent().build();
    }

    private UUID currentUserId(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"))
                .getId();
    }
}

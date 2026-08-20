package com.heartbeat.ping.controller;

import com.heartbeat.ping.dto.statuspage.StatusPageRequest;
import com.heartbeat.ping.dto.statuspage.StatusPageResponse;
import com.heartbeat.ping.service.StatusPageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Authenticated CRUD for the caller's own status pages. See {@link PublicStatusPageController} for the public read side. */
@RestController
@RequestMapping("/api/v1/status-pages")
@RequiredArgsConstructor
public class StatusPageController {

    private final StatusPageService statusPageService;

    @PostMapping
    public ResponseEntity<StatusPageResponse> create(
            @Valid @RequestBody StatusPageRequest request, Authentication authentication) {
        return new ResponseEntity<>(
                statusPageService.create(authentication.getName(), request), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<StatusPageResponse>> list(Authentication authentication) {
        return ResponseEntity.ok(statusPageService.list(authentication.getName()));
    }

    @GetMapping("/{pageId}")
    public ResponseEntity<StatusPageResponse> get(
            @PathVariable UUID pageId, Authentication authentication) {
        return ResponseEntity.ok(statusPageService.get(authentication.getName(), pageId));
    }

    @PutMapping("/{pageId}")
    public ResponseEntity<StatusPageResponse> update(
            @PathVariable UUID pageId,
            @Valid @RequestBody StatusPageRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(statusPageService.update(authentication.getName(), pageId, request));
    }

    @DeleteMapping("/{pageId}")
    public ResponseEntity<Void> delete(@PathVariable UUID pageId, Authentication authentication) {
        statusPageService.delete(authentication.getName(), pageId);
        return ResponseEntity.noContent().build();
    }
}

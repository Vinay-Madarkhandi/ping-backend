package com.heartbeat.ping.controller;

import com.heartbeat.ping.dto.statuspage.PublicStatusPageResponse;
import com.heartbeat.ping.service.StatusPageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Unauthenticated (permitAll — see SpringSecurity) read of a published status page. Kept as its
 * own controller, distinct from {@link StatusPageController}, so the permitAll path match in
 * SpringSecurity can target this whole prefix without accidentally opening the owner-only CRUD.
 */
@RestController
@RequestMapping("/api/v1/public/status-pages")
@RequiredArgsConstructor
public class PublicStatusPageController {

    private final StatusPageService statusPageService;

    @GetMapping("/{slug}")
    public ResponseEntity<PublicStatusPageResponse> get(@PathVariable String slug) {
        return ResponseEntity.ok(statusPageService.getPublic(slug));
    }
}

package com.heartbeat.ping.controller;

import com.heartbeat.ping.dto.statuspage.PublicStatusPageResponse;
import com.heartbeat.ping.dto.statuspage.UnlockStatusPageRequest;
import com.heartbeat.ping.service.StatusPageService;
import com.heartbeat.ping.service.security.RateLimitExceededException;
import com.heartbeat.ping.service.security.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

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
    private final RateLimiter rateLimiter;

    /** Throws {@code StatusPagePasswordException} (mapped to 401) when the page is password protected. */
    @GetMapping("/{slug}")
    public ResponseEntity<PublicStatusPageResponse> get(@PathVariable String slug) {
        return ResponseEntity.ok(statusPageService.getPublic(slug));
    }

    /**
     * Verifies a password against a protected page; 401 on a wrong password. Rate-limited by slug
     * (bounds repeated guesses against one page regardless of how many source IPs an attacker
     * rotates through) and by IP (bounds one attacker spraying guesses across many pages) — the
     * same two-key pattern {@code AuthController} uses for sign-in.
     */
    @PostMapping("/{slug}/unlock")
    public ResponseEntity<PublicStatusPageResponse> unlock(
            @PathVariable String slug,
            @Valid @RequestBody UnlockStatusPageRequest request,
            HttpServletRequest httpRequest) {
        if (!rateLimiter.allow("status-page-unlock:slug:" + slug, 10, Duration.ofMinutes(15))
                || !rateLimiter.allow("status-page-unlock:ip:" + clientIp(httpRequest), 30, Duration.ofMinutes(15))) {
            throw new RateLimitExceededException("Too many attempts. Please try again later.");
        }
        return ResponseEntity.ok(statusPageService.unlockPublic(slug, request.getPassword()));
    }

    private String clientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}

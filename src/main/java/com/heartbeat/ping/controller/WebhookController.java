package com.heartbeat.ping.controller;

import com.heartbeat.ping.service.billing.WebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Razorpay webhook receiver (server-to-server, no JWT — see SpringSecurity permitAll). The signature
 * is computed over the EXACT raw body, so we take {@code @RequestBody String} (never a parsed DTO).
 * Always returns 200 once verified; Razorpay retries on non-2xx.
 */
@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookService webhookService;

    @PostMapping("/razorpay")
    public ResponseEntity<Void> razorpay(
            @RequestBody String rawPayload,
            @RequestHeader("X-Razorpay-Signature") String signature
    ) {
        webhookService.handle(rawPayload, signature);
        return ResponseEntity.ok().build();
    }
}

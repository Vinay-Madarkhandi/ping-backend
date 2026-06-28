package com.heartbeat.ping.controller;

import com.heartbeat.ping.service.billing.BillingService;
import com.heartbeat.ping.service.billing.dto.CreateOrderRequest;
import com.heartbeat.ping.service.billing.dto.CreateOrderResponse;
import com.heartbeat.ping.service.billing.dto.VerifyPaymentRequest;
import com.heartbeat.ping.service.billing.dto.VerifyPaymentResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-serve plan upgrades. Order amounts are resolved server-side; the actual upgrade only happens
 * after cryptographic verification. The frontend is UI-only — it never sets the plan or the amount.
 */
@RestController
@RequestMapping("/api/v1/billing")
@RequiredArgsConstructor
public class BillingController {

    private final BillingService billingService;

    @PostMapping("/orders")
    public ResponseEntity<CreateOrderResponse> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(billingService.createOrder(authentication.getName(), request.getTargetPlan()));
    }

    @PostMapping("/verify")
    public ResponseEntity<VerifyPaymentResponse> verify(
            @Valid @RequestBody VerifyPaymentRequest request,
            Authentication authentication
    ) {
        String plan = billingService.fulfill(
                request.getRazorpayOrderId(),
                request.getRazorpayPaymentId(),
                request.getRazorpaySignature(),
                true);
        return ResponseEntity.ok(new VerifyPaymentResponse(true, plan));
    }
}

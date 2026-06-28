package com.heartbeat.ping.service.billing.dto;

public record VerifyPaymentResponse(
        boolean success,
        String plan
) {
}

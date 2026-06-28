package com.heartbeat.ping.service.billing.dto;

/** Checkout payload for the Razorpay widget. Amount/currency are authoritative (set by the backend). */
public record CreateOrderResponse(
        String razorpayOrderId,
        long amount,
        String currency,
        String keyId
) {
}

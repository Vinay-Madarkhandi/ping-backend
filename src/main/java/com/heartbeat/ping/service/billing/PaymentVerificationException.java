package com.heartbeat.ping.service.billing;

/** Thrown when a payment or webhook signature fails verification. Mapped to HTTP 400 (no detail leaked). */
public class PaymentVerificationException extends RuntimeException {
    public PaymentVerificationException(String message) {
        super(message);
    }
}

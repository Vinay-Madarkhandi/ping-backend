package com.heartbeat.ping.service.billing;

/** Thrown for invalid billing requests (unknown/non-purchasable plan, etc.). Mapped to HTTP 400. */
public class BillingException extends RuntimeException {
    public BillingException(String message) {
        super(message);
    }
}

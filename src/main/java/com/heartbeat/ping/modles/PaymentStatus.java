package com.heartbeat.ping.modles;

/** Lifecycle of a payment attempt recorded in {@code payment_transactions}. */
public enum PaymentStatus {
    CREATED,
    PENDING,
    SUCCESS,
    FAILED,
    REFUNDED
}

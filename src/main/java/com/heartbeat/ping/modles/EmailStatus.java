package com.heartbeat.ping.modles;

/** Delivery state of an outbox email. FAILED is the dead-letter terminal state. */
public enum EmailStatus {
    PENDING,
    SENT,
    FAILED
}

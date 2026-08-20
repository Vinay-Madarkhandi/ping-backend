package com.heartbeat.ping.modles;

/** Delivery state of an outbox webhook. FAILED is the dead-letter terminal state. */
public enum WebhookStatus {
    PENDING,
    SENT,
    FAILED
}

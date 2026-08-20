package com.heartbeat.ping.service.execution;

/**
 * Thrown when on-demand checks for a monitor are requested faster than the cooldown allows.
 * Mapped to HTTP 429 by the global handler.
 */
public class ManualCheckThrottledException extends RuntimeException {

    public ManualCheckThrottledException(String message) {
        super(message);
    }
}

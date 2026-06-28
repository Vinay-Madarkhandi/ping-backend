package com.heartbeat.ping.service;

/**
 * Thrown when a request would exceed the user's plan limits (too many monitors, interval below the
 * plan minimum, or timeout above the plan maximum). Mapped to HTTP 403 by the global handler.
 */
public class PlanLimitExceededException extends RuntimeException {
    public PlanLimitExceededException(String message) {
        super(message);
    }
}

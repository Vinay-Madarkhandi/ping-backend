package com.heartbeat.ping.service.security;

/** Thrown when a monitor URL targets a disallowed (private/internal/metadata) destination. */
public class SsrfValidationException extends RuntimeException {

    public SsrfValidationException(String message) {
        super(message);
    }
}

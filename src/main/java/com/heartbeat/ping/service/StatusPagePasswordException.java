package com.heartbeat.ping.service;

/**
 * Thrown when a password-protected status page is requested without the correct password —
 * either none was supplied ({@link #required()}) or the supplied one was wrong. Mapped to HTTP 401
 * by the global handler; the caller is expected to prompt for (or re-prompt for) a password and
 * retry against {@code POST /api/v1/public/status-pages/{slug}/unlock}.
 */
public class StatusPagePasswordException extends RuntimeException {

    public StatusPagePasswordException(String message) {
        super(message);
    }

    public static StatusPagePasswordException required() {
        return new StatusPagePasswordException("This status page is password protected");
    }

    public static StatusPagePasswordException incorrect() {
        return new StatusPagePasswordException("Incorrect password");
    }
}

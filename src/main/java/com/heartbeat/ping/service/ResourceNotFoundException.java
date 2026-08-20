package com.heartbeat.ping.service;

/**
 * Thrown when a request references a resource that does not exist (e.g. a user looked up by a
 * JWT subject that no longer resolves). Mapped to HTTP 404 by the global handler — unlike a bare
 * {@code RuntimeException}, which falls through to Spring's default error handling and can leak
 * an internal stack trace to the client.
 */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}

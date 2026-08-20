package com.heartbeat.ping.service.security;

/**
 * Thrown when an abuse-sensitive endpoint (signin, signup, forgot-password, resend-verification)
 * is called faster than {@link RateLimiter} allows. Mapped to HTTP 429 by the global handler.
 */
public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException(String message) {
        super(message);
    }
}

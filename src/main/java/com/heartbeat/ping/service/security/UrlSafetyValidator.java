package com.heartbeat.ping.service.security;

/**
 * Validates that an outbound monitor URL is safe to request, guarding against SSRF.
 * Implementations throw {@link SsrfValidationException} when a URL is rejected.
 */
public interface UrlSafetyValidator {

    void validate(String url);
}

package com.heartbeat.ping.service.security;

import com.heartbeat.ping.config.properties.SsrfProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Set;

/**
 * Create-time SSRF guard: rejects non-HTTP(S) schemes and URLs whose host resolves to a
 * disallowed address (per {@link IpAddressPolicy}). This is the fail-fast check on monitor
 * creation; the authoritative runtime guard against DNS rebinding is the validating DNS resolver
 * wired into the HTTP client (so the IP that is connected to is the IP that was validated).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SsrfGuard implements UrlSafetyValidator {

    private static final Set<String> BLOCKED_HOSTNAMES = Set.of(
            "localhost",
            "metadata.google.internal"
    );

    private final SsrfProperties props;
    private final IpAddressPolicy addressPolicy;

    @Override
    public void validate(String url) {
        if (!props.isEnabled()) {
            return;
        }

        URI uri = parse(url);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new SsrfValidationException("Only http/https URLs are allowed");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new SsrfValidationException("URL host is missing");
        }

        checkHost(host);
    }

    @Override
    public void validateHost(String host) {
        if (!props.isEnabled()) {
            return;
        }
        if (host == null || host.isBlank()) {
            throw new SsrfValidationException("Host is missing");
        }
        checkHost(host);
    }

    private void checkHost(String host) {
        if (props.isAllowPrivate()) {
            return; // dev/test escape hatch
        }

        if (BLOCKED_HOSTNAMES.contains(host.toLowerCase())) {
            throw new SsrfValidationException("Host '" + host + "' is not allowed");
        }

        for (InetAddress address : resolve(host)) {
            if (addressPolicy.isBlocked(address)) {
                throw new SsrfValidationException(
                        "Host '" + host + "' resolves to a disallowed address: " + address.getHostAddress());
            }
        }
    }

    private URI parse(String url) {
        try {
            return new URI(url.trim());
        } catch (Exception e) {
            throw new SsrfValidationException("Malformed URL: " + url);
        }
    }

    private InetAddress[] resolve(String host) {
        try {
            return InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new SsrfValidationException("Host '" + host + "' could not be resolved");
        }
    }
}

package com.heartbeat.ping.service.security;

import java.net.UnknownHostException;

/**
 * Thrown by the validating DNS resolver when a host resolves to a disallowed address at
 * connection time. Extends {@link UnknownHostException} so it fits the HC5 {@code DnsResolver}
 * contract; the health check recognises it and reports an INCONCLUSIVE (not DOWN) outcome.
 */
public class SsrfBlockedHostException extends UnknownHostException {

    public SsrfBlockedHostException(String message) {
        super(message);
    }
}

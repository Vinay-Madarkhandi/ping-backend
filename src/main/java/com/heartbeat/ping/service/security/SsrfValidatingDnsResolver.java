package com.heartbeat.ping.service.security;

import com.heartbeat.ping.config.properties.SsrfProperties;
import lombok.RequiredArgsConstructor;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * DNS resolver installed into the HTTP connection manager. It performs the <em>single</em>
 * resolution that the client then connects to, and rejects the host if any resolved address is
 * disallowed. Because there is no second, unchecked resolution, a DNS-rebinding attacker cannot
 * present a safe IP to a validator and a private IP to the socket — the validated IP is the
 * connected IP. Disabled (pass-through) when SSRF protection is off or private hosts are allowed.
 */
@Component
@RequiredArgsConstructor
public class SsrfValidatingDnsResolver extends SystemDefaultDnsResolver {

    private final SsrfProperties props;
    private final IpAddressPolicy addressPolicy;

    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        InetAddress[] addresses = super.resolve(host);
        if (!props.isEnabled() || props.isAllowPrivate()) {
            return addresses;
        }
        for (InetAddress address : addresses) {
            if (addressPolicy.isBlocked(address)) {
                throw new SsrfBlockedHostException(
                        "Host '" + host + "' resolved to a disallowed address: " + address.getHostAddress());
            }
        }
        return addresses;
    }
}

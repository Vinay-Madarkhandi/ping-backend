package com.heartbeat.ping.service.security;

import org.springframework.stereotype.Component;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Arrays;

/**
 * Decides whether a resolved IP address is a disallowed SSRF target (loopback, private,
 * link-local, multicast, IPv6 unique-local, or cloud metadata). Shared by the create-time
 * {@link SsrfGuard} and the connection-time validating DNS resolver so both apply identical rules.
 */
@Component
public class IpAddressPolicy {

    /** Alibaba Cloud metadata endpoint (AWS/GCP/Azure 169.254.169.254 is already link-local). */
    private static final byte[] ALIBABA_METADATA = {(byte) 100, (byte) 100, (byte) 100, (byte) 200};

    public boolean isBlocked(InetAddress address) {
        return address.isLoopbackAddress()
                || address.isAnyLocalAddress()
                || address.isLinkLocalAddress()   // covers 169.254.0.0/16 cloud metadata
                || address.isSiteLocalAddress()   // covers 10/8, 172.16/12, 192.168/16
                || address.isMulticastAddress()
                || isUniqueLocalIpv6(address)
                || isExplicitMetadata(address);
    }

    private boolean isUniqueLocalIpv6(InetAddress address) {
        return address instanceof Inet6Address && (address.getAddress()[0] & 0xfe) == 0xfc;
    }

    private boolean isExplicitMetadata(InetAddress address) {
        return Arrays.equals(address.getAddress(), ALIBABA_METADATA);
    }
}

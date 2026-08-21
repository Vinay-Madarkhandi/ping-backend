package com.heartbeat.ping.service.check;

import com.heartbeat.ping.config.properties.SsrfProperties;
import com.heartbeat.ping.service.security.IpAddressPolicy;
import com.heartbeat.ping.service.security.SsrfBlockedHostException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;

/**
 * Raw TCP reachability check for {@code TCP}-kind monitors: no HTTP request, just "does a
 * connection to host:port succeed". Runs entirely outside any transaction, like
 * {@link HttpHealthCheckService}.
 *
 * <p>SSRF protection mirrors the HTTP path's approach but has to be hand-rolled here, since there
 * is no HTTP client to install a validating DNS resolver into: this resolves the host once,
 * rejects it if any address is disallowed (unless {@code allowPrivate}), and connects directly to
 * the validated {@link InetAddress} object — never re-resolving the hostname — so a DNS-rebinding
 * attacker cannot present a safe IP to the check and a private IP to the actual connection.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TcpHealthCheckService {

    private final SsrfProperties ssrfProps;
    private final IpAddressPolicy addressPolicy;

    public CheckResult check(CheckSpec spec) {
        long start = System.currentTimeMillis();
        try {
            InetAddress address = resolveAndValidate(spec.url());
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(address, spec.port()), (int) effectiveTimeout(spec));
            }
            long elapsed = System.currentTimeMillis() - start;
            return CheckResult.up(0, elapsed);
        } catch (UnknownHostException ex) {
            long elapsed = System.currentTimeMillis() - start;
            log.warn("Monitor {} TCP check failed to resolve host: {}", spec.monitorId(), ex.getMessage());
            // Blocked-by-policy is our infrastructure's decision, not a target failure; a host that
            // genuinely doesn't resolve is the target's fault, same DOWN-vs-INCONCLUSIVE split HTTP uses.
            return ex instanceof SsrfBlockedHostException
                    ? CheckResult.inconclusive(elapsed, ex.getMessage())
                    : CheckResult.down(0, elapsed, ex.getMessage());
        } catch (Exception ex) {
            long elapsed = System.currentTimeMillis() - start;
            log.warn("Monitor {} TCP check failed: {}", spec.monitorId(), ex.getMessage());
            return CheckResult.down(0, elapsed, ex.getMessage());
        }
    }

    private InetAddress resolveAndValidate(String host) throws UnknownHostException {
        InetAddress[] addresses = InetAddress.getAllByName(host);
        if (!ssrfProps.isEnabled() || ssrfProps.isAllowPrivate()) {
            return addresses[0];
        }
        for (InetAddress address : addresses) {
            if (!addressPolicy.isBlocked(address)) {
                return address;
            }
        }
        throw new SsrfBlockedHostException(
                "Host '" + host + "' resolves only to disallowed addresses");
    }

    private long effectiveTimeout(CheckSpec spec) {
        return spec.timeoutMillis() > 0 ? spec.timeoutMillis() : 5000;
    }
}

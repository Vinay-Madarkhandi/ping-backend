package com.heartbeat.ping.service.check;

import com.heartbeat.ping.config.properties.SsrfProperties;
import com.heartbeat.ping.modles.MonitorKind;
import com.heartbeat.ping.modles.MonitorMethod;
import com.heartbeat.ping.service.security.IpAddressPolicy;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TcpHealthCheckServiceTest {

    private CheckSpec specFor(String host, int port) {
        return new CheckSpec(UUID.randomUUID(), UUID.randomUUID(), MonitorKind.TCP, host, port,
                MonitorMethod.GET, 1000, null, null, true, Map.of());
    }

    @Test
    void reportsUpWhenThePortAcceptsAConnection() throws IOException {
        SsrfProperties props = new SsrfProperties();
        props.setAllowPrivate(true); // loopback is otherwise blocked by policy
        TcpHealthCheckService service = new TcpHealthCheckService(props, new IpAddressPolicy());

        try (ServerSocket serverSocket = new ServerSocket(0)) {
            CheckResult result = service.check(specFor("127.0.0.1", serverSocket.getLocalPort()));

            assertThat(result.up()).isTrue();
        }
    }

    @Test
    void reportsDownWhenNothingIsListeningOnThePort() throws IOException {
        SsrfProperties props = new SsrfProperties();
        props.setAllowPrivate(true);
        TcpHealthCheckService service = new TcpHealthCheckService(props, new IpAddressPolicy());

        int freePort;
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            freePort = serverSocket.getLocalPort();
        } // closed immediately — nothing listens on it now

        CheckResult result = service.check(specFor("127.0.0.1", freePort));

        assertThat(result.up()).isFalse();
        assertThat(result.outcome().name()).isEqualTo("DOWN");
    }

    @Test
    void reportsInconclusiveWhenTheHostIsBlockedByPolicy() {
        SsrfProperties props = new SsrfProperties(); // enabled, allowPrivate=false — the default
        TcpHealthCheckService service = new TcpHealthCheckService(props, new IpAddressPolicy());

        CheckResult result = service.check(specFor("127.0.0.1", 5432));

        assertThat(result.outcome().name()).isEqualTo("INCONCLUSIVE");
    }

    @Test
    void reportsDownWhenTheHostDoesNotResolve() {
        SsrfProperties props = new SsrfProperties();
        props.setAllowPrivate(true);
        TcpHealthCheckService service = new TcpHealthCheckService(props, new IpAddressPolicy());

        CheckResult result = service.check(specFor("this-host-should-not-resolve.invalid", 80));

        assertThat(result.outcome().name()).isEqualTo("DOWN");
    }
}

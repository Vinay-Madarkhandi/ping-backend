package com.heartbeat.ping.service.check;

import com.heartbeat.ping.modles.Monitor;
import com.heartbeat.ping.modles.MonitorKind;
import com.heartbeat.ping.modles.MonitorMethod;

import java.util.Map;
import java.util.UUID;

/**
 * Immutable snapshot of everything needed to probe a monitor, decoupled from the JPA entity.
 * Built inside a read transaction and handed to the (transaction-free) health check, so the
 * network call never holds a database connection.
 *
 * <p>{@code kind} lets {@link com.heartbeat.ping.service.execution.MonitorCheckRunner} decide
 * whether to probe at all: a {@code HEARTBEAT} spec being claimed means no ping arrived in time,
 * so the runner records DOWN directly instead of calling the health check service.
 */
public record CheckSpec(
        UUID monitorId,
        UUID userId,
        MonitorKind kind,
        String url,
        /** TCP monitors only. */
        Integer port,
        MonitorMethod method,
        int timeoutMillis,
        Integer expectedStatusCode,
        String keyword,
        boolean followRedirects,
        Map<String, String> customHeaders
) {

    public static CheckSpec from(Monitor monitor) {
        return new CheckSpec(
                monitor.getId(),
                // Reading the id off the lazy proxy does not initialise the User (no extra query).
                monitor.getUser() != null ? monitor.getUser().getId() : null,
                monitor.getKind() != null ? monitor.getKind() : MonitorKind.HTTP,
                monitor.getUrl(),
                monitor.getPort(),
                monitor.getMonitorMethod() != null ? monitor.getMonitorMethod() : MonitorMethod.GET,
                monitor.getTimeoutMilliseconds(),
                monitor.getExpectedStatusCode(),
                monitor.getKeyword(),
                monitor.isFollowRedirects(),
                monitor.getCustomHeaders() != null ? Map.copyOf(monitor.getCustomHeaders()) : Map.of()
        );
    }
}

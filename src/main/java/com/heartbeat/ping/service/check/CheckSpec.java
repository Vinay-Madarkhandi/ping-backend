package com.heartbeat.ping.service.check;

import com.heartbeat.ping.modles.Monitor;
import com.heartbeat.ping.modles.MonitorMethod;

import java.util.Map;
import java.util.UUID;

/**
 * Immutable snapshot of everything needed to probe a monitor, decoupled from the JPA entity.
 * Built inside a read transaction and handed to the (transaction-free) health check, so the
 * network call never holds a database connection.
 */
public record CheckSpec(
        UUID monitorId,
        UUID userId,
        String url,
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
                monitor.getUrl(),
                monitor.getMonitorMethod() != null ? monitor.getMonitorMethod() : MonitorMethod.GET,
                monitor.getTimeoutMilliseconds(),
                monitor.getExpectedStatusCode(),
                monitor.getKeyword(),
                monitor.isFollowRedirects(),
                monitor.getCustomHeaders() != null ? Map.copyOf(monitor.getCustomHeaders()) : Map.of()
        );
    }
}

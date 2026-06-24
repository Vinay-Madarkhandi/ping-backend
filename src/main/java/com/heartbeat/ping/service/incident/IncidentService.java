package com.heartbeat.ping.service.incident;

import com.heartbeat.ping.modles.Incident;
import com.heartbeat.ping.modles.Monitor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages outage incidents. Always invoked inside the alert engine's transaction (under the
 * monitor_status lock), so open/resolve are atomic with the state transition that triggers them.
 */
public interface IncidentService {

    /** Opens a new incident for a monitor that has just been confirmed DOWN. */
    Incident open(Monitor monitor, String failureReason, Instant startedAt);

    /** Resolves the monitor's open incident (if any), stamping recovery time and duration. */
    Optional<Incident> resolve(UUID monitorId, Instant resolvedAt);

    Page<Incident> history(UUID monitorId, Pageable pageable);
}

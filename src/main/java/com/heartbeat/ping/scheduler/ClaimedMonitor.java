package com.heartbeat.ping.scheduler;

import java.util.UUID;

/**
 * A monitor that has been atomically claimed for execution: its id, how late it was
 * (scheduler lag in millis), and its effective timeout (used to route it to the
 * fast or slow bulkhead pool).
 */
public record ClaimedMonitor(UUID monitorId, long lagMs, long timeoutMillis) {
}

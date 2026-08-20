package com.heartbeat.ping.dto.monitor;

import java.time.Instant;

/** Acknowledges a received heartbeat ping and tells the caller when the next one is expected. */
public record HeartbeatPingResponse(String monitor, Instant nextExpectedBy) {
}

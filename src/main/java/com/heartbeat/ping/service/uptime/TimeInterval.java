package com.heartbeat.ping.service.uptime;

import java.time.Instant;

/** A half-open time interval [start, end). */
public record TimeInterval(Instant start, Instant end) {
}

package com.heartbeat.ping.service.time;

import java.time.Instant;

/**
 * Source of "now" anchored to the database clock rather than per-JVM wall time, so leases,
 * incident timestamps and durations are immune to cross-node clock skew in a multi-instance
 * deployment. Within one transaction this returns the (fixed) transaction-start time.
 */
public interface DatabaseClock {

    Instant now();
}

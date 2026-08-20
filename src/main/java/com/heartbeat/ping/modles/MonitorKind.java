package com.heartbeat.ping.modles;

/**
 * What kind of thing a monitor watches. {@code HTTP} is the original probe-based monitor (Ping
 * calls the target on an interval). {@code HEARTBEAT} inverts that: an external job (cron, worker,
 * backup script) calls Ping instead, and the monitor goes DOWN if no ping arrives in time.
 */
public enum MonitorKind {
    HTTP,
    HEARTBEAT
}

package com.heartbeat.ping.modles;

/** Confirmed availability state of a monitor, as decided by the alert engine. */
public enum MonitorState {
    /** No checks recorded yet. */
    UNKNOWN,
    UP,
    /** Failing but below the failure threshold — not yet a confirmed outage. */
    SUSPECT,
    DOWN
}

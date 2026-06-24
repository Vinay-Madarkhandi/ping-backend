package com.heartbeat.ping.service.alert;

import com.heartbeat.ping.modles.Monitor;
import com.heartbeat.ping.modles.MonitorStatus;
import com.heartbeat.ping.service.check.CheckResult;

import java.time.Instant;

/**
 * Drives the monitor's UP/SUSPECT/DOWN state machine from a check result and applies the
 * side effects of transitions (open/resolve incident, enqueue alert). Must be called inside a
 * transaction with {@code status} already loaded under a pessimistic lock, so transitions for the
 * same monitor are linearizable. {@code now} is the database-clock time for this check.
 */
public interface AlertEngine {

    void process(Monitor monitor, MonitorStatus status, CheckResult result, Instant now);
}

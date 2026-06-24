package com.heartbeat.ping.service.metrics;

import com.heartbeat.ping.modles.ProbeOutcome;

/** Records monitoring telemetry (latency, scheduler lag, alert/email counters) to Micrometer. */
public interface MetricsService {

    /** Records the latency and outcome (up/down/inconclusive) of a single health check. */
    void recordCheck(ProbeOutcome outcome, long latencyMs);

    /** Records how far behind schedule a claimed monitor was when it ran. */
    void recordSchedulerLag(long lagMs);

    /** Records that a claimed check was dropped because the given pool ("fast"/"slow") was saturated. */
    void recordCheckRejected(String pool);

    /** Records an alert state transition that produced a notification ("down"/"recovery"). */
    void recordAlert(String type);

    /** Records an outbox email outcome ("sent"/"retry"/"failed"). */
    void recordEmail(String outcome);
}

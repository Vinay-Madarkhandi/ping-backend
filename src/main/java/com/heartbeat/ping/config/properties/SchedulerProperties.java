package com.heartbeat.ping.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Tuning for the polling scheduler and its check-execution thread pool ({@code monitor.scheduler.*}). */
@Getter
@Setter
@ConfigurationProperties(prefix = "monitor.scheduler")
public class SchedulerProperties {

    /** Master switch for the polling scheduler (disabled in tests / passive nodes). */
    private boolean enabled = true;

    /** How often the poller looks for due monitors. */
    private long pollRateMs = 5_000;

    /**
     * Safety margin added on top of {@code max(interval, timeout)} when leasing a claimed monitor,
     * so a still-running check is never re-claimed before it finishes.
     */
    private Duration leaseMargin = Duration.ofSeconds(5);

    /** Max monitors claimed per poll (further capped by available executor capacity). */
    private int batchSize = 100;

    /** Monitors whose effective timeout is at least this go to the slow bulkhead pool. */
    private long slowThresholdMs = 5_000;
}

package com.heartbeat.ping.config.properties;

import lombok.Getter;
import lombok.Setter;

/**
 * Reusable sizing for a check-execution thread pool. Bound twice (fast / slow) under
 * {@code monitor.executor.fast.*} and {@code monitor.executor.slow.*} for bulkhead isolation.
 */
@Getter
@Setter
public class WorkerPoolProperties {

    private int corePoolSize = 8;
    private int maxPoolSize = 16;
    private int queueCapacity = 200;
}

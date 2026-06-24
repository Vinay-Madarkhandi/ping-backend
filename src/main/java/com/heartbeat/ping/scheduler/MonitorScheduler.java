package com.heartbeat.ping.scheduler;

import com.heartbeat.ping.config.CheckExecutorConfig;
import com.heartbeat.ping.config.properties.SchedulerProperties;
import com.heartbeat.ping.service.execution.MonitorCheckRunner;
import com.heartbeat.ping.service.metrics.MetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

/**
 * Thin poller. Each tick it sizes the claim to available executor capacity (adaptive
 * backpressure — no claiming work it can't run), atomically claims due monitors, and routes
 * each to the fast or slow bulkhead pool by its timeout. All execution happens off the polling
 * thread so a slow check never blocks claiming.
 *
 * <p>Disabled via {@code monitor.scheduler.enabled=false} (e.g. in tests or passive nodes).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "monitor.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class MonitorScheduler {

    private final MonitorClaimService claimService;
    private final MonitorCheckRunner checkRunner;
    private final MetricsService metricsService;
    private final SchedulerProperties props;

    @Qualifier(CheckExecutorConfig.FAST_CHECK_EXECUTOR)
    private final ThreadPoolTaskExecutor fastExecutor;

    @Qualifier(CheckExecutorConfig.SLOW_CHECK_EXECUTOR)
    private final ThreadPoolTaskExecutor slowExecutor;

    @Scheduled(fixedDelayString = "${monitor.scheduler.poll-rate-ms:5000}")
    public void poll() {
        try {
            int capacity = Math.min(props.getBatchSize(), freeSlots(fastExecutor) + freeSlots(slowExecutor));
            if (capacity <= 0) {
                return; // pools saturated — don't claim work we can't run
            }

            List<ClaimedMonitor> claimed = claimService.claimDue(capacity);
            for (ClaimedMonitor monitor : claimed) {
                metricsService.recordSchedulerLag(monitor.lagMs());
                dispatch(monitor);
            }
        } catch (Exception e) {
            // Never let a poll failure (e.g. DB failover) kill the schedule; the next tick retries.
            log.error("Monitor poll failed; will retry next tick", e);
        }
    }

    private void dispatch(ClaimedMonitor monitor) {
        boolean slow = monitor.timeoutMillis() >= props.getSlowThresholdMs();
        ThreadPoolTaskExecutor executor = slow ? slowExecutor : fastExecutor;
        String pool = slow ? "slow" : "fast";
        try {
            executor.execute(() -> runSafely(monitor.monitorId()));
        } catch (RejectedExecutionException rejected) {
            // Pool saturated: drop this one. Its lease will expire and it gets re-claimed later.
            metricsService.recordCheckRejected(pool);
            log.warn("{} pool saturated; dropping check for monitor {} (will be re-claimed)", pool, monitor.monitorId());
        }
    }

    private void runSafely(UUID monitorId) {
        try {
            checkRunner.run(monitorId);
        } catch (Exception e) {
            log.error("Check execution failed for monitor {}", monitorId, e);
        }
    }

    /** Heuristic free capacity: open queue slots plus idle threads. AbortPolicy covers estimation races. */
    private int freeSlots(ThreadPoolTaskExecutor executor) {
        var pool = executor.getThreadPoolExecutor();
        int idleThreads = Math.max(0, pool.getMaximumPoolSize() - pool.getActiveCount());
        return pool.getQueue().remainingCapacity() + idleThreads;
    }
}

package com.heartbeat.ping.scheduler;

import com.heartbeat.ping.service.lock.DistributedLock;
import com.heartbeat.ping.service.retention.RetentionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Daily trigger for log rollup + purge. Cluster-singleton: only the instance that wins the
 * {@code retention} leader lock runs; the others skip this tick. Shares the scheduling pool and is
 * disabled entirely when the scheduler is off.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "monitor.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class RetentionJob {

    private static final String LOCK_NAME = "retention";
    private static final long LOCK_TTL_SECONDS = Duration.ofMinutes(30).toSeconds();

    private final RetentionService retentionService;
    private final DistributedLock distributedLock;

    @Scheduled(cron = "${monitor.retention.cron:0 30 3 * * *}")
    public void run() {
        if (!distributedLock.tryAcquire(LOCK_NAME, LOCK_TTL_SECONDS)) {
            log.debug("Retention lock held by another instance; skipping this tick");
            return;
        }
        try {
            retentionService.runRetention();
        } catch (Exception e) {
            log.error("Retention job failed; will retry on the next schedule", e);
        }
    }
}

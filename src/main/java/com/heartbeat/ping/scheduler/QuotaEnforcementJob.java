package com.heartbeat.ping.scheduler;

import com.heartbeat.ping.service.QuotaEnforcementService;
import com.heartbeat.ping.service.lock.DistributedLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Periodically reconciles per-user monthly quota against the {@code quota_blocked} flag.
 * Cluster-singleton: only the instance that wins the {@code quota} leader lock runs each tick.
 * Shares the scheduling pool and is disabled entirely when the scheduler is off.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "monitor.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class QuotaEnforcementJob {

    private static final String LOCK_NAME = "quota";
    private static final long LOCK_TTL_SECONDS = Duration.ofMinutes(10).toSeconds();

    private final QuotaEnforcementService quotaEnforcementService;
    private final DistributedLock distributedLock;

    @Scheduled(fixedDelayString = "${monitor.quota.enforce-rate-ms:300000}")
    public void run() {
        if (!distributedLock.tryAcquire(LOCK_NAME, LOCK_TTL_SECONDS)) {
            log.debug("Quota lock held by another instance; skipping this tick");
            return;
        }
        try {
            quotaEnforcementService.enforce();
        } catch (Exception e) {
            log.error("Quota enforcement failed; will retry on the next tick", e);
        }
    }
}

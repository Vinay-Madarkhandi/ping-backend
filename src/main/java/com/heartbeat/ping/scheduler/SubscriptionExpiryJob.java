package com.heartbeat.ping.scheduler;

import com.heartbeat.ping.service.billing.SubscriptionService;
import com.heartbeat.ping.service.lock.DistributedLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Periodically downgrades users whose paid subscription has lapsed back to FREE. Cluster-singleton:
 * only the instance that wins the {@code subscription} leader lock runs each tick. Shares the
 * scheduling pool and is disabled entirely when the scheduler is off.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "monitor.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class SubscriptionExpiryJob {

    private static final String LOCK_NAME = "subscription";
    private static final long LOCK_TTL_SECONDS = Duration.ofMinutes(10).toSeconds();

    private final SubscriptionService subscriptionService;
    private final DistributedLock distributedLock;

    @Scheduled(cron = "${monitor.billing.expiry-cron:0 15 * * * *}")
    public void run() {
        if (!distributedLock.tryAcquire(LOCK_NAME, LOCK_TTL_SECONDS)) {
            log.debug("Subscription lock held by another instance; skipping this tick");
            return;
        }
        try {
            subscriptionService.downgradeExpired();
        } catch (Exception e) {
            log.error("Subscription expiry job failed; will retry on the next schedule", e);
        }
    }
}

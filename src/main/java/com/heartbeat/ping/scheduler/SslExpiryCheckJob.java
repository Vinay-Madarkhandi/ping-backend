package com.heartbeat.ping.scheduler;

import com.heartbeat.ping.modles.MonitorStatus;
import com.heartbeat.ping.repository.MonitorStatusRepository;
import com.heartbeat.ping.service.lock.DistributedLock;
import com.heartbeat.ping.service.notification.NotificationService;
import com.heartbeat.ping.service.time.DatabaseClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Warns monitor owners once per certificate when a monitor's TLS certificate — captured
 * opportunistically during normal HTTPS checks — is due to expire soon. Cluster-singleton via the
 * {@code ssl-expiry} leader lock, same pattern as the other maintenance jobs.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "monitor.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class SslExpiryCheckJob {

    private static final String LOCK_NAME = "ssl-expiry";
    private static final long LOCK_TTL_SECONDS = Duration.ofMinutes(10).toSeconds();

    private final MonitorStatusRepository statusRepository;
    private final NotificationService notificationService;
    private final DistributedLock distributedLock;
    private final DatabaseClock clock;

    @Value("${monitor.ssl-expiry.warning-days:14}")
    private int warningDays;

    @Scheduled(cron = "${monitor.ssl-expiry.cron:0 30 4 * * *}")
    public void run() {
        if (!distributedLock.tryAcquire(LOCK_NAME, LOCK_TTL_SECONDS)) {
            log.debug("SSL expiry lock held by another instance; skipping this tick");
            return;
        }
        try {
            checkExpiringCertificates();
        } catch (Exception e) {
            log.error("SSL expiry check job failed; will retry on the next schedule", e);
        }
    }

    @Transactional(readOnly = true)
    void checkExpiringCertificates() {
        Instant now = clock.now();
        LocalDateTime nowUtc = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        LocalDateTime threshold = nowUtc.plusDays(warningDays);

        List<MonitorStatus> expiring = statusRepository.findSslExpiringBetween(nowUtc, threshold);
        for (MonitorStatus status : expiring) {
            Instant expiresAt = status.getSslCertExpiresAt().toInstant(ZoneOffset.UTC);
            long daysRemaining = ChronoUnit.DAYS.between(nowUtc, status.getSslCertExpiresAt());
            notificationService.enqueueSslExpiryWarning(status.getMonitor(), expiresAt, daysRemaining, now);
        }
    }
}

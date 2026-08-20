package com.heartbeat.ping.scheduler;

import com.heartbeat.ping.service.EmailVerificationService;
import com.heartbeat.ping.service.JwtService;
import com.heartbeat.ping.service.PasswordResetService;
import com.heartbeat.ping.service.lock.DistributedLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Periodically purges expired password-reset tokens, email-verification tokens, and revoked-JWT
 * records. These three cleanup methods existed but were never wired to a schedule, so the tables
 * grew unbounded. Cluster-singleton via the {@code token-cleanup} leader lock, same pattern as the
 * other maintenance jobs.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "monitor.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class TokenCleanupJob {

    private static final String LOCK_NAME = "token-cleanup";
    private static final long LOCK_TTL_SECONDS = Duration.ofMinutes(10).toSeconds();

    private final PasswordResetService passwordResetService;
    private final EmailVerificationService emailVerificationService;
    private final JwtService jwtService;
    private final DistributedLock distributedLock;

    @Scheduled(cron = "${monitor.token-cleanup.cron:0 0 4 * * *}")
    public void run() {
        if (!distributedLock.tryAcquire(LOCK_NAME, LOCK_TTL_SECONDS)) {
            log.debug("Token cleanup lock held by another instance; skipping this tick");
            return;
        }
        try {
            passwordResetService.cleanupExpiredTokens();
            emailVerificationService.cleanupExpiredTokens();
            jwtService.cleanupExpiredRevokedTokens();
        } catch (Exception e) {
            log.error("Token cleanup job failed; will retry on the next schedule", e);
        }
    }
}

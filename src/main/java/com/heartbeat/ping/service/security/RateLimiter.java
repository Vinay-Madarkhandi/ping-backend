package com.heartbeat.ping.service.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fixed-window request limiter for abuse-sensitive, unauthenticated-or-self-service endpoints
 * (signin, signup, forgot-password, resend-verification). In-memory and therefore per-instance —
 * abuse mitigation, not a distributed guarantee, matching the same trade-off as
 * {@link com.heartbeat.ping.service.execution.ManualCheckService}'s manual-check cooldown.
 *
 * <p>Keys are caller-controlled (email, IP), so entries are pruned periodically to bound memory
 * rather than relying solely on lazy per-key expiry.
 */
@Slf4j
@Component
public class RateLimiter {

    private record Window(Instant startedAt, AtomicInteger count) {
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    /**
     * Returns true if the caller identified by {@code key} may proceed — i.e. this attempt keeps
     * the count within {@code maxAttempts} for the current {@code window}. Every call (allowed or
     * not) counts as an attempt.
     */
    public boolean allow(String key, int maxAttempts, Duration window) {
        Instant now = Instant.now();
        Window current = windows.compute(key, (k, existing) -> {
            if (existing == null || Duration.between(existing.startedAt(), now).compareTo(window) >= 0) {
                return new Window(now, new AtomicInteger(1));
            }
            existing.count().incrementAndGet();
            return existing;
        });
        return current.count().get() <= maxAttempts;
    }

    /** Drops windows that ended well in the past, so abuse across many distinct keys can't leak memory. */
    @Scheduled(fixedRate = 10, timeUnit = java.util.concurrent.TimeUnit.MINUTES)
    public void pruneStaleWindows() {
        Instant cutoff = Instant.now().minus(Duration.ofHours(1));
        int before = windows.size();
        windows.values().removeIf(w -> w.startedAt().isBefore(cutoff));
        int removed = before - windows.size();
        if (removed > 0) {
            log.debug("Pruned {} stale rate-limit windows", removed);
        }
    }
}

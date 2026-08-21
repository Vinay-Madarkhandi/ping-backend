package com.heartbeat.ping.service;

import com.heartbeat.ping.dto.statuspage.PublicStatusPageResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Short-TTL cache for the public status page projection, keyed by slug. {@code getPublic} is
 * fully unauthenticated and its cost scales with monitor count — building the response runs
 * 2-3 queries per attached monitor (incidents, pause windows, gap detection) — so without a
 * cache every hit on a public page, however innocuous or malicious, recomputes that from
 * scratch. In-memory and per-instance (abuse/load mitigation, not a distributed guarantee),
 * matching {@link com.heartbeat.ping.service.security.RateLimiter}'s trade-off.
 *
 * <p>A short TTL is an acceptable staleness window here: the page already reflects a 10s+
 * check cadence, so numbers a few seconds old are the norm, not a regression. Writes call
 * {@link #invalidate} so an edited page does not keep serving its pre-edit snapshot for the
 * remainder of the TTL.
 */
@Slf4j
@Component
public class PublicStatusPageCache {

    private static final Duration TTL = Duration.ofSeconds(20);

    private record Entry(PublicStatusPageResponse value, Instant expiresAt) {
    }

    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    public PublicStatusPageResponse getOrCompute(String slug, Supplier<PublicStatusPageResponse> compute) {
        Instant now = Instant.now();
        Entry existing = cache.get(slug);
        if (existing != null && existing.expiresAt().isAfter(now)) {
            return existing.value();
        }

        PublicStatusPageResponse value = compute.get();
        cache.put(slug, new Entry(value, now.plus(TTL)));
        return value;
    }

    public void invalidate(String slug) {
        cache.remove(slug);
    }

    /** Drops expired entries so slugs that are no longer being hit don't linger forever. */
    @Scheduled(fixedRate = 10, timeUnit = java.util.concurrent.TimeUnit.MINUTES)
    public void pruneExpired() {
        Instant now = Instant.now();
        int before = cache.size();
        cache.values().removeIf(entry -> !entry.expiresAt().isAfter(now));
        int removed = before - cache.size();
        if (removed > 0) {
            log.debug("Pruned {} expired public status page cache entries", removed);
        }
    }
}

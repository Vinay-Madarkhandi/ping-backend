package com.heartbeat.ping.service;

import com.heartbeat.ping.dto.statuspage.PublicStatusPageResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class PublicStatusPageCacheTest {

    private final PublicStatusPageCache cache = new PublicStatusPageCache();

    private PublicStatusPageResponse response(String title) {
        return new PublicStatusPageResponse(title, null, null, "OPERATIONAL", List.of(), Instant.now());
    }

    @Test
    void secondCallForTheSameSlugDoesNotRecompute() {
        AtomicInteger computations = new AtomicInteger();

        cache.getOrCompute("acme", () -> {
            computations.incrementAndGet();
            return response("Acme");
        });
        PublicStatusPageResponse second = cache.getOrCompute("acme", () -> {
            computations.incrementAndGet();
            return response("Acme");
        });

        assertThat(computations.get()).isEqualTo(1);
        assertThat(second.title()).isEqualTo("Acme");
    }

    @Test
    void differentSlugsAreCachedIndependently() {
        cache.getOrCompute("acme", () -> response("Acme"));
        cache.getOrCompute("globex", () -> response("Globex"));

        AtomicInteger computations = new AtomicInteger();
        PublicStatusPageResponse acme = cache.getOrCompute("acme", () -> {
            computations.incrementAndGet();
            return response("Acme (recomputed)");
        });

        assertThat(computations.get()).isZero();
        assertThat(acme.title()).isEqualTo("Acme");
    }

    @Test
    void invalidateForcesRecomputationOnTheNextCall() {
        cache.getOrCompute("acme", () -> response("Acme"));
        cache.invalidate("acme");

        AtomicInteger computations = new AtomicInteger();
        cache.getOrCompute("acme", () -> {
            computations.incrementAndGet();
            return response("Acme (fresh)");
        });

        assertThat(computations.get()).isEqualTo(1);
    }
}

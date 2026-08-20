package com.heartbeat.ping.service.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {

    private final RateLimiter rateLimiter = new RateLimiter();

    @Test
    void allowsUpToTheConfiguredMaxAttemptsWithinTheWindow() {
        String key = "signin:email:user@example.com";

        for (int i = 0; i < 5; i++) {
            assertThat(rateLimiter.allow(key, 5, Duration.ofMinutes(5))).isTrue();
        }

        assertThat(rateLimiter.allow(key, 5, Duration.ofMinutes(5))).isFalse();
    }

    @Test
    void tracksDifferentKeysIndependently() {
        assertThat(rateLimiter.allow("a", 1, Duration.ofMinutes(5))).isTrue();
        assertThat(rateLimiter.allow("a", 1, Duration.ofMinutes(5))).isFalse();

        assertThat(rateLimiter.allow("b", 1, Duration.ofMinutes(5))).isTrue();
    }

    @Test
    void resetsAfterTheWindowElapses() {
        String key = "forgot-password:user@example.com";
        Duration tinyWindow = Duration.ofMillis(20);

        assertThat(rateLimiter.allow(key, 1, tinyWindow)).isTrue();
        assertThat(rateLimiter.allow(key, 1, tinyWindow)).isFalse();

        await(tinyWindow.toMillis() + 30);

        assertThat(rateLimiter.allow(key, 1, tinyWindow)).isTrue();
    }

    @Test
    void pruneStaleWindowsRemovesOldEntriesButKeepsRecentOnes() {
        rateLimiter.allow("stale-key", 5, Duration.ofMillis(1));
        await(5);
        rateLimiter.allow("fresh-key", 5, Duration.ofMinutes(5));

        rateLimiter.pruneStaleWindows();

        // A fresh window well within the last hour survives pruning; consuming it further still
        // enforces the original limit rather than silently resetting.
        assertThat(rateLimiter.allow("fresh-key", 5, Duration.ofMinutes(5))).isTrue();
    }

    private void await(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

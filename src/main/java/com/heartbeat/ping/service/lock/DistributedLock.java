package com.heartbeat.ping.service.lock;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Database-backed leader lock for cluster-singleton scheduled jobs. Acquisition is a single
 * conditional UPDATE evaluated entirely on the database clock (skew-free across instances); the
 * lock auto-expires after {@code ttlSeconds}, so a crashed holder cannot block the job forever.
 * No explicit release — the TTL is sized to exceed the job's runtime but be well under its schedule.
 */
@Component
@RequiredArgsConstructor
public class DistributedLock {

    /** Stable per-instance identity, for diagnostics in {@code scheduler_lock.locked_by}. */
    private static final String OWNER = UUID.randomUUID().toString();

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public boolean tryAcquire(String name, long ttlSeconds) {
        int updated = jdbcTemplate.update("""
                update scheduler_lock
                set locked_until = current_timestamp + (? * interval '1 second'), locked_by = ?
                where name = ? and locked_until <= current_timestamp
                """, ttlSeconds, OWNER, name);
        return updated == 1;
    }
}

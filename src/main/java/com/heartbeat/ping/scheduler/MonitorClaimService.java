package com.heartbeat.ping.scheduler;

import com.heartbeat.ping.config.properties.HttpClientProperties;
import com.heartbeat.ping.config.properties.SchedulerProperties;
import com.heartbeat.ping.modles.Monitor;
import com.heartbeat.ping.repository.MonitorRepository;
import com.heartbeat.ping.service.time.DatabaseClock;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Atomically claims and leases a batch of due monitors in a single transaction.
 * Locking the rows ({@code FOR UPDATE SKIP LOCKED}) and pushing {@code nextCheckAt} forward by a
 * full lease window means no other poller — in this instance or another — picks the same monitor
 * before its runner finishes. The runner overwrites {@code nextCheckAt} with the true next due
 * time on completion; if it crashes, the lease simply expires and the monitor is retried.
 */
@Service
@RequiredArgsConstructor
public class MonitorClaimService {

    private final MonitorRepository monitorRepository;
    private final SchedulerProperties schedulerProps;
    private final HttpClientProperties httpProps;
    private final DatabaseClock clock;

    @Transactional
    public List<ClaimedMonitor> claimDue(int batchSize) {
        if (batchSize <= 0) {
            return List.of();
        }
        Instant now = clock.now();
        List<Monitor> due = monitorRepository.claimDueMonitors(PageRequest.of(0, batchSize));

        return due.stream()
                .map(monitor -> {
                    long timeoutMs = effectiveTimeoutMs(monitor);
                    long lagMs = Duration.between(monitor.getNextCheckAt(), now).toMillis();
                    lease(monitor, now, timeoutMs);
                    return new ClaimedMonitor(monitor.getId(), Math.max(0, lagMs), timeoutMs);
                })
                .toList();
    }

    /**
     * Leases the monitor for {@code max(interval, timeout) + margin} so an in-flight check
     * (which can run up to its timeout) is never re-claimed mid-execution.
     */
    private void lease(Monitor monitor, Instant now, long timeoutMs) {
        long busyMs = Math.max(monitor.getIntervalMilliseconds(), timeoutMs);
        monitor.setNextCheckAt(now.plus(Duration.ofMillis(busyMs)).plus(schedulerProps.getLeaseMargin()));
        monitorRepository.save(monitor);
    }

    private long effectiveTimeoutMs(Monitor monitor) {
        return monitor.getTimeoutMilliseconds() > 0
                ? monitor.getTimeoutMilliseconds()
                : httpProps.getResponseTimeout().toMillis();
    }
}

package com.heartbeat.ping.scheduler;

import com.heartbeat.ping.modles.Monitor;
import com.heartbeat.ping.modles.MonitorMethod;
import com.heartbeat.ping.repository.MonitorRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the Postgres-only claim path (FOR UPDATE SKIP LOCKED + DB-clock lease) and the full
 * Flyway migration set against a real Postgres, which H2 cannot validate.
 */
@SpringBootTest
@Testcontainers
class MonitorClaimIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MonitorRepository monitorRepository;

    @Autowired
    private MonitorClaimService claimService;

    private Monitor persistMonitor(boolean active, boolean paused, boolean archived, Instant nextCheckAt) {
        Monitor monitor = Monitor.builder()
                .name("m-" + UUID.randomUUID())
                .url("https://example.com")
                .intervalMilliseconds(60_000)
                .timeoutMilliseconds(5_000)
                .isActive(active)
                .paused(paused)
                .monitorMethod(MonitorMethod.GET)
                .followRedirects(true)
                .nextCheckAt(nextCheckAt)
                .build();
        if (archived) {
            monitor.setDeletedAt(Instant.now());
        }
        return monitorRepository.save(monitor);
    }

    @Test
    void claimsDueMonitorAndLeasesItForward() {
        Monitor due = persistMonitor(true, false, false, Instant.now().minusSeconds(120));

        List<ClaimedMonitor> claimed = claimService.claimDue(50);

        assertThat(claimed).extracting(ClaimedMonitor::monitorId).contains(due.getId());

        Monitor reloaded = monitorRepository.findById(due.getId()).orElseThrow();
        // Leased forward by max(interval, timeout) + margin (>= 60s), so it is no longer due.
        assertThat(reloaded.getNextCheckAt()).isAfter(Instant.now());
    }

    @Test
    void doesNotClaimPausedOrArchivedMonitors() {
        Monitor paused = persistMonitor(true, true, false, Instant.now().minusSeconds(120));
        Monitor archived = persistMonitor(true, false, true, Instant.now().minusSeconds(120));

        List<UUID> claimed = claimService.claimDue(50).stream().map(ClaimedMonitor::monitorId).toList();

        assertThat(claimed).doesNotContain(paused.getId(), archived.getId());
    }
}

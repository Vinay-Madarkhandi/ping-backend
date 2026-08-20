package com.heartbeat.ping.service.execution;

import com.heartbeat.ping.modles.Monitor;
import com.heartbeat.ping.modles.MonitorKind;
import com.heartbeat.ping.modles.MonitorStatus;
import com.heartbeat.ping.repository.MonitorLogsRepository;
import com.heartbeat.ping.repository.MonitorRepository;
import com.heartbeat.ping.repository.MonitorStatusRepository;
import com.heartbeat.ping.repository.UserUsageRepository;
import com.heartbeat.ping.service.alert.AlertEngine;
import com.heartbeat.ping.service.check.CheckResult;
import com.heartbeat.ping.service.time.DatabaseClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Covers {@code reschedule}'s kind-aware branch specifically: a HTTP monitor's next check is
 * {@code now + interval}, but a HEARTBEAT monitor's next deadline is {@code now + interval + grace}
 * — the grace period exists precisely to absorb job-runtime jitter without a false DOWN.
 */
@ExtendWith(MockitoExtension.class)
class MonitorCheckTransactionServiceTest {

    @Mock private MonitorRepository monitorRepository;
    @Mock private MonitorLogsRepository monitorLogsRepository;
    @Mock private MonitorStatusRepository statusRepository;
    @Mock private UserUsageRepository userUsageRepository;
    @Mock private AlertEngine alertEngine;
    @Mock private DatabaseClock clock;

    private MonitorCheckTransactionService service;
    private final Instant now = Instant.parse("2026-08-20T10:00:00Z");

    @BeforeEach
    void setUp() {
        service = new MonitorCheckTransactionService(
                monitorRepository, monitorLogsRepository, statusRepository, userUsageRepository, alertEngine, clock);
        lenient().when(clock.now()).thenReturn(now);
        lenient().when(statusRepository.findByIdForUpdate(any())).thenReturn(Optional.empty());
    }

    @Test
    void httpMonitorReschedulesByIntervalAlone() {
        Monitor monitor = monitor(MonitorKind.HTTP, 60_000, 0);
        when(monitorRepository.findById(monitor.getId())).thenReturn(Optional.of(monitor));

        service.recordResult(monitor.getId(), null, CheckResult.up(200, 10));

        assertThat(monitor.getNextCheckAt()).isEqualTo(now.plusSeconds(60));
    }

    @Test
    void heartbeatMonitorReschedulesByIntervalPlusGrace() {
        Monitor monitor = monitor(MonitorKind.HEARTBEAT, 300_000, 60_000);
        when(monitorRepository.findById(monitor.getId())).thenReturn(Optional.of(monitor));

        service.recordResult(monitor.getId(), null, CheckResult.up(200, 0));

        assertThat(monitor.getNextCheckAt()).isEqualTo(now.plusSeconds(360));
    }

    private Monitor monitor(MonitorKind kind, int intervalMs, int graceMs) {
        Monitor monitor = Monitor.builder()
                .name("m")
                .kind(kind)
                .intervalMilliseconds(intervalMs)
                .gracePeriodMilliseconds(graceMs)
                .timeoutMilliseconds(5_000)
                .build();
        monitor.setId(UUID.randomUUID());
        return monitor;
    }
}

package com.heartbeat.ping.service.execution;

import com.heartbeat.ping.modles.MonitorKind;
import com.heartbeat.ping.modles.MonitorMethod;
import com.heartbeat.ping.service.check.CheckResult;
import com.heartbeat.ping.service.check.CheckSpec;
import com.heartbeat.ping.service.check.HealthCheckService;
import com.heartbeat.ping.service.metrics.MetricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonitorCheckRunnerTest {

    @Mock private MonitorCheckTransactionService transactionService;
    @Mock private HealthCheckService healthCheckService;
    @Mock private MetricsService metricsService;

    private MonitorCheckRunner runner;

    @BeforeEach
    void setUp() {
        runner = new MonitorCheckRunner(transactionService, healthCheckService, metricsService);
    }

    @Test
    void heartbeatMonitorIsRecordedDownWithoutProbing() {
        UUID monitorId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        CheckSpec spec = new CheckSpec(monitorId, userId, MonitorKind.HEARTBEAT, null, MonitorMethod.GET,
                5_000, null, null, true, Map.of());
        when(transactionService.loadSpec(monitorId)).thenReturn(Optional.of(spec));

        Optional<CheckResult> result = runner.runAndReport(monitorId);

        assertThat(result).isPresent();
        assertThat(result.get().outcome().name()).isEqualTo("DOWN");
        verify(healthCheckService, never()).check(any());
        verify(transactionService).recordResult(eq(monitorId), eq(userId), eq(result.get()));
    }

    @Test
    void httpMonitorIsProbedNormally() {
        UUID monitorId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        CheckSpec spec = new CheckSpec(monitorId, userId, MonitorKind.HTTP, "https://example.com", MonitorMethod.GET,
                5_000, null, null, true, Map.of());
        CheckResult probeResult = CheckResult.up(200, 42);
        when(transactionService.loadSpec(monitorId)).thenReturn(Optional.of(spec));
        when(healthCheckService.check(spec)).thenReturn(probeResult);

        Optional<CheckResult> result = runner.runAndReport(monitorId);

        assertThat(result).contains(probeResult);
        verify(transactionService).recordResult(monitorId, userId, probeResult);
    }
}

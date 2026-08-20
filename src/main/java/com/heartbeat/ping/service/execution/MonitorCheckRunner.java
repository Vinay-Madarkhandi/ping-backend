package com.heartbeat.ping.service.execution;

import com.heartbeat.ping.modles.MonitorKind;
import com.heartbeat.ping.service.check.CheckResult;
import com.heartbeat.ping.service.check.CheckSpec;
import com.heartbeat.ping.service.check.HealthCheckService;
import com.heartbeat.ping.service.metrics.MetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates a single monitor check on a worker thread, with no transaction of its own:
 * it loads an immutable spec (short read tx), runs the HTTP probe with no connection held, then
 * persists the result (short write tx). Runtime SSRF protection is enforced inside the HTTP client's
 * validating DNS resolver, so the IP connected to is the IP that was validated.
 *
 * <p>{@code HEARTBEAT} monitors never reach {@link HealthCheckService} — the scheduler only claims
 * one when its lease (pushed forward by the last received ping) has expired, which means the
 * expected ping did not arrive. That claim itself is the failure signal, so the runner records DOWN
 * directly. A successful ping is recorded separately, by {@link HeartbeatService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonitorCheckRunner {

    private final MonitorCheckTransactionService transactionService;
    private final HealthCheckService healthCheckService;
    private final MetricsService metricsService;

    public void run(UUID monitorId) {
        runAndReport(monitorId);
    }

    /**
     * Same pipeline as {@link #run(UUID)} but returns the outcome, so an on-demand check can report
     * back to the caller. Empty when the monitor was archived between claim and execution.
     */
    public Optional<CheckResult> runAndReport(UUID monitorId) {
        Optional<CheckSpec> spec = transactionService.loadSpec(monitorId);
        if (spec.isEmpty()) {
            return Optional.empty(); // deleted between claim and execution
        }

        CheckResult result = spec.get().kind() == MonitorKind.HEARTBEAT
                ? CheckResult.down(0, 0, "No heartbeat ping received within the expected window")
                : healthCheckService.check(spec.get());
        transactionService.recordResult(monitorId, spec.get().userId(), result);
        metricsService.recordCheck(result.outcome(), result.responseTimeMs());
        return Optional.of(result);
    }
}

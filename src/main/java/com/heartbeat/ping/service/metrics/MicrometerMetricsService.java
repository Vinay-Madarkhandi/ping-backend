package com.heartbeat.ping.service.metrics;

import com.heartbeat.ping.modles.ProbeOutcome;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Micrometer-backed metrics, exported via the Prometheus actuator endpoint. */
@Service
public class MicrometerMetricsService implements MetricsService {

    private final MeterRegistry registry;
    private final AtomicLong quotaBlockedMonitors = new AtomicLong(0);

    public MicrometerMetricsService(MeterRegistry registry) {
        this.registry = registry;
    }

    @PostConstruct
    void bindGauges() {
        registry.gauge("monitor.quota.blocked_monitors", quotaBlockedMonitors);
    }

    @Override
    public void recordCheck(ProbeOutcome outcome, long latencyMs) {
        String result = outcome.name().toLowerCase(Locale.ROOT);
        registry.timer("monitor.check.latency", "result", result)
                .record(latencyMs, TimeUnit.MILLISECONDS);
        registry.counter("monitor.checks.total", "result", result).increment();
    }

    @Override
    public void recordSchedulerLag(long lagMs) {
        registry.summary("monitor.scheduler.lag.ms").record(lagMs);
    }

    @Override
    public void recordCheckRejected(String pool) {
        registry.counter("monitor.checks.rejected", "pool", pool).increment();
    }

    @Override
    public void recordAlert(String type) {
        registry.counter("monitor.alerts", "type", type).increment();
    }

    @Override
    public void recordAlertSuppressed(String reason) {
        registry.counter("monitor.alerts.suppressed", "reason", reason).increment();
    }

    @Override
    public void setQuotaBlockedMonitors(long count) {
        quotaBlockedMonitors.set(count);
    }

    @Override
    public void recordEmail(String outcome) {
        registry.counter("monitor.email.outbox", "outcome", outcome).increment();
    }
}

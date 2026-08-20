package com.heartbeat.ping.service.execution;

import com.heartbeat.ping.dto.monitor.ManualCheckResponse;
import com.heartbeat.ping.modles.Monitor;
import com.heartbeat.ping.repository.MonitorRepository;
import com.heartbeat.ping.service.check.CheckResult;
import com.heartbeat.ping.service.time.DatabaseClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs a monitor's check immediately at the user's request ("Check now"), instead of waiting for the
 * next scheduled poll. This is the shortest feedback loop the product can offer after a fix.
 *
 * <p>The probe goes through the normal pipeline, so it is a genuine observation: it writes a log row,
 * consumes quota, and can drive the alert state machine. That is intentional — a manual check that did
 * not count would be a lie.
 *
 * <p>Because it is user-triggered outbound HTTP, it is rate limited per monitor. The limiter is
 * in-memory and therefore per-instance: it is abuse mitigation, not a distributed guarantee. A
 * cluster-wide limit belongs with the rest of the rate-limiting work rather than here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ManualCheckService {

    /** Minimum gap between on-demand checks of the same monitor. */
    private static final Duration COOLDOWN = Duration.ofSeconds(10);

    private final MonitorRepository monitorRepository;
    private final MonitorCheckRunner checkRunner;
    private final DatabaseClock clock;

    private final Map<UUID, Instant> lastManualCheck = new ConcurrentHashMap<>();

    public ManualCheckResponse checkNow(UUID monitorId, UUID userId) {
        Monitor monitor = monitorRepository.findByIdAndUser_Id(monitorId, userId)
                .filter(m -> m.getDeletedAt() == null)
                .orElseThrow(() -> new AccessDeniedException("Monitor not found for this user"));

        Instant now = clock.now();
        enforceCooldown(monitor.getId(), now);

        CheckResult result = checkRunner.runAndReport(monitorId)
                .orElseThrow(() -> new IllegalStateException("Monitor is no longer checkable"));

        log.debug("Manual check for monitor {} returned {}", monitorId, result.outcome());
        return new ManualCheckResponse(
                result.outcome(),
                result.statusCode(),
                result.responseTimeMs(),
                result.errorMessage(),
                now);
    }

    private void enforceCooldown(UUID monitorId, Instant now) {
        Instant previous = lastManualCheck.get(monitorId);
        if (previous != null && Duration.between(previous, now).compareTo(COOLDOWN) < 0) {
            throw new ManualCheckThrottledException(
                    "Please wait a few seconds before checking this monitor again");
        }
        lastManualCheck.put(monitorId, now);
    }
}

package com.heartbeat.ping.service.execution;

import com.heartbeat.ping.dto.monitor.HeartbeatPingResponse;
import com.heartbeat.ping.modles.Monitor;
import com.heartbeat.ping.modles.MonitorKind;
import com.heartbeat.ping.repository.MonitorRepository;
import com.heartbeat.ping.service.ResourceNotFoundException;
import com.heartbeat.ping.service.check.CheckResult;
import com.heartbeat.ping.service.time.DatabaseClock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Records pings from external jobs against HEARTBEAT monitors — the "success" half of heartbeat
 * monitoring. The "failure" half (no ping arrived in time) needs no code here at all: it is the
 * existing scheduler claiming the monitor once its lease (pushed forward by the last recorded ping)
 * expires, which {@link MonitorCheckRunner} then records as DOWN.
 *
 * <p>The endpoint behind this service is public — reachable by anyone with the token, by design, the
 * same way a status page slug is. A misconfigured or retrying client hammering it must not be able to
 * flood the database or burn the owner's check quota, so pings within the debounce window are
 * coalesced silently (still 200, just no side effects) rather than rejected: a retrying cron job
 * should never see an error for pinging "too often". Debouncing on the last <em>recorded</em> ping
 * (not the last received one) matters — debouncing on "last received" would mean a client pinging
 * faster than the window never gets a single ping recorded, so its deadline would never advance and
 * it would incorrectly go DOWN.
 */
@Service
@RequiredArgsConstructor
public class HeartbeatService {

    private static final Duration DEBOUNCE = Duration.ofSeconds(2);

    private final MonitorRepository monitorRepository;
    private final MonitorCheckTransactionService transactionService;
    private final DatabaseClock clock;

    private final Map<UUID, Instant> lastRecordedAt = new ConcurrentHashMap<>();

    public HeartbeatPingResponse recordPing(String token) {
        Monitor monitor = monitorRepository.findByHeartbeatTokenAndDeletedAtIsNull(token)
                .filter(m -> m.getKind() == MonitorKind.HEARTBEAT)
                .orElseThrow(() -> new ResourceNotFoundException("Unknown heartbeat token"));

        Instant now = clock.now();
        if (shouldRecord(monitor.getId(), now)) {
            UUID userId = monitor.getUser() != null ? monitor.getUser().getId() : null;
            transactionService.recordResult(monitor.getId(), userId, CheckResult.up(200, 0));
        }

        long delayMs = monitor.getIntervalMilliseconds() + (long) monitor.getGracePeriodMilliseconds();
        return new HeartbeatPingResponse(monitor.getName(), now.plusMillis(delayMs));
    }

    private boolean shouldRecord(UUID monitorId, Instant now) {
        Instant previous = lastRecordedAt.get(monitorId);
        if (previous != null && Duration.between(previous, now).compareTo(DEBOUNCE) < 0) {
            return false;
        }
        lastRecordedAt.put(monitorId, now);
        return true;
    }
}

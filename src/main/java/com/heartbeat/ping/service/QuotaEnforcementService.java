package com.heartbeat.ping.service;

import com.heartbeat.ping.repository.MonitorRepository;
import com.heartbeat.ping.repository.UserUsageRepository;
import com.heartbeat.ping.service.metrics.MetricsService;
import com.heartbeat.ping.service.time.DatabaseClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Reconciles the {@code quota_blocked} flag on monitors with each user's monthly consumption:
 * monitors owned by over-quota users are blocked from scheduling, everyone else is unblocked.
 * Runs periodically (see {@code QuotaEnforcementJob}); month rollover empties the period's usage,
 * so blocks clear automatically without any reset step.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuotaEnforcementService {

    private final UserUsageRepository userUsageRepository;
    private final MonitorRepository monitorRepository;
    private final MetricsService metricsService;
    private final DatabaseClock clock;

    @Transactional
    public void enforce() {
        LocalDate period = LocalDate.ofInstant(clock.now(), ZoneOffset.UTC).withDayOfMonth(1);
        List<UUID> overQuota = userUsageRepository.findOverQuotaUserIds(period);

        int blocked;
        if (overQuota.isEmpty()) {
            blocked = 0;
            monitorRepository.unblockAll();
        } else {
            blocked = monitorRepository.blockForUsers(overQuota);
            monitorRepository.unblockExceptUsers(overQuota);
        }

        long totalBlocked = monitorRepository.countByQuotaBlockedTrue();
        metricsService.setQuotaBlockedMonitors(totalBlocked);
        if (blocked > 0) {
            log.info("Quota enforcement: {} user(s) over quota, {} monitor(s) newly blocked, {} blocked total",
                    overQuota.size(), blocked, totalBlocked);
        }
    }
}

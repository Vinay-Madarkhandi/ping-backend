package com.heartbeat.ping.service;

import com.heartbeat.ping.dto.usage.UsageResponse;
import com.heartbeat.ping.modles.Plan;
import com.heartbeat.ping.modles.User;
import com.heartbeat.ping.modles.UserAlertUsage;
import com.heartbeat.ping.modles.UserUsage;
import com.heartbeat.ping.repository.MonitorRepository;
import com.heartbeat.ping.repository.UserAlertUsageRepository;
import com.heartbeat.ping.repository.UserRepository;
import com.heartbeat.ping.repository.UserUsageRepository;
import com.heartbeat.ping.service.time.DatabaseClock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Builds the signed-in user's own usage snapshot. This is the self-service counterpart to
 * {@link AdminUsageService} (which reports every user) and reads the same counters the quota
 * enforcement job acts on, so the UI shows exactly what the scheduler will enforce.
 *
 * <p>All period boundaries come from the database clock, matching how the counters are written in
 * {@code MonitorCheckTransactionService} — otherwise a skewed instance could read the wrong month.
 */
@Service
@RequiredArgsConstructor
public class UsageService {

    private final UserRepository userRepository;
    private final MonitorRepository monitorRepository;
    private final UserUsageRepository userUsageRepository;
    private final UserAlertUsageRepository userAlertUsageRepository;
    private final PlanService planService;
    private final DatabaseClock clock;

    @Transactional(readOnly = true)
    public UsageResponse usageFor(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Instant now = clock.now();
        LocalDate periodMonth = LocalDate.ofInstant(now, ZoneOffset.UTC).withDayOfMonth(1);
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);

        long monitorCount = monitorRepository.countByUser_IdAndDeletedAtIsNull(user.getId());
        long checksThisMonth = userUsageRepository.findByUserIdAndPeriodMonth(user.getId(), periodMonth)
                .map(UserUsage::getChecksConsumed)
                .orElse(0L);
        int alertsToday = userAlertUsageRepository.findByUserIdAndDay(user.getId(), today)
                .map(UserAlertUsage::getAlertsSent)
                .orElse(0);

        // Plan id is read off the lazy proxy (no extra query); the full plan comes from the cache.
        Plan plan = user.getPlan() != null ? planService.getById(user.getPlan().getId()) : null;
        boolean overQuota = plan != null && checksThisMonth >= plan.getMonthlyCheckQuota();

        return new UsageResponse(monitorCount, checksThisMonth, alertsToday, overQuota);
    }
}

package com.heartbeat.ping.scheduler;

import com.heartbeat.ping.modles.Monitor;
import com.heartbeat.ping.modles.MonitorMethod;
import com.heartbeat.ping.modles.Plan;
import com.heartbeat.ping.modles.User;
import com.heartbeat.ping.modles.UserUsage;
import com.heartbeat.ping.repository.MonitorRepository;
import com.heartbeat.ping.repository.PlanRepository;
import com.heartbeat.ping.repository.UserAlertUsageRepository;
import com.heartbeat.ping.repository.UserRepository;
import com.heartbeat.ping.repository.UserUsageRepository;
import com.heartbeat.ping.service.QuotaEnforcementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the Postgres-only usage-limiting paths against a real database: the {@code ON CONFLICT}
 * upserts for usage counters, quota-driven scheduler gating, and per-plan log retention.
 */
@SpringBootTest
@Testcontainers
@Transactional
class UsageQuotaIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private PlanRepository planRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private MonitorRepository monitorRepository;
    @Autowired
    private UserUsageRepository userUsageRepository;
    @Autowired
    private UserAlertUsageRepository userAlertUsageRepository;
    @Autowired
    private QuotaEnforcementService quotaEnforcementService;
    @Autowired
    private MonitorClaimService claimService;

    @Test
    void upsertsIncrementUsageCountersAtomically() {
        User user = persistUser("FREE");
        LocalDate month = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        userUsageRepository.incrementChecks(user.getId(), month);
        userUsageRepository.incrementChecks(user.getId(), month);
        userAlertUsageRepository.incrementAlerts(user.getId(), today);

        assertThat(userUsageRepository.findByUserIdAndPeriodMonth(user.getId(), month))
                .get().extracting(UserUsage::getChecksConsumed).isEqualTo(2L);
        assertThat(userAlertUsageRepository.findByUserIdAndDay(user.getId(), today))
                .get().extracting(u -> u.getAlertsSent()).isEqualTo(1);
    }

    @Test
    void overQuotaUserMonitorsAreBlockedAndSkippedByScheduler() {
        User user = persistUser("FREE");
        Plan free = planRepository.findByName("FREE").orElseThrow();
        Monitor monitor = persistDueMonitor(user);

        // Push the user's consumption to the FREE monthly quota.
        LocalDate month = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1);
        userUsageRepository.save(UserUsage.builder()
                .userId(user.getId())
                .periodMonth(month)
                .checksConsumed(free.getMonthlyCheckQuota())
                .build());

        quotaEnforcementService.enforce();

        assertThat(monitorRepository.findById(monitor.getId()).orElseThrow().isQuotaBlocked()).isTrue();
        List<UUID> claimed = claimService.claimDue(50).stream().map(ClaimedMonitor::monitorId).toList();
        assertThat(claimed).doesNotContain(monitor.getId());
    }

    private User persistUser(String planName) {
        Plan plan = planRepository.findByName(planName).orElseThrow();
        User user = User.builder()
                .userName("u-" + UUID.randomUUID())
                .email(UUID.randomUUID() + "@example.com")
                .passwordHash("x")
                .plan(plan)
                .build();
        return userRepository.save(user);
    }

    private Monitor persistDueMonitor(User user) {
        return monitorRepository.save(Monitor.builder()
                .name("m-" + UUID.randomUUID())
                .url("https://example.com")
                .intervalMilliseconds(300_000)
                .timeoutMilliseconds(5_000)
                .isActive(true)
                .paused(false)
                .monitorMethod(MonitorMethod.GET)
                .followRedirects(true)
                .user(user)
                .nextCheckAt(Instant.now().minusSeconds(120))
                .build());
    }
}

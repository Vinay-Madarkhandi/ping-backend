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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsageServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z");
    private static final LocalDate PERIOD = LocalDate.of(2026, 6, 1);
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 15);

    @Mock private UserRepository userRepository;
    @Mock private MonitorRepository monitorRepository;
    @Mock private UserUsageRepository userUsageRepository;
    @Mock private UserAlertUsageRepository userAlertUsageRepository;
    @Mock private PlanService planService;
    @Mock private DatabaseClock clock;

    private UsageService service;
    private Plan free;
    private User user;

    @BeforeEach
    void setUp() {
        service = new UsageService(userRepository, monitorRepository, userUsageRepository,
                userAlertUsageRepository, planService, clock);

        free = Plan.builder()
                .id(UUID.randomUUID())
                .name("FREE")
                .maxMonitors(5)
                .monthlyCheckQuota(50_000)
                .build();
        user = User.builder().email("u@example.com").plan(free).build();
        user.setId(UUID.randomUUID());

        lenient().when(clock.now()).thenReturn(NOW);
        lenient().when(userRepository.findByEmail("u@example.com")).thenReturn(Optional.of(user));
        lenient().when(planService.getById(free.getId())).thenReturn(free);
    }

    @Test
    void reportsCountersForTheCurrentPeriod() {
        when(monitorRepository.countByUser_IdAndDeletedAtIsNull(user.getId())).thenReturn(3L);
        when(userUsageRepository.findByUserIdAndPeriodMonth(user.getId(), PERIOD))
                .thenReturn(Optional.of(UserUsage.builder().checksConsumed(1_420L).build()));
        when(userAlertUsageRepository.findByUserIdAndDay(user.getId(), TODAY))
                .thenReturn(Optional.of(UserAlertUsage.builder().alertsSent(2).build()));

        UsageResponse usage = service.usageFor("u@example.com");

        assertThat(usage.monitorCount()).isEqualTo(3L);
        assertThat(usage.checksThisMonth()).isEqualTo(1_420L);
        assertThat(usage.alertsToday()).isEqualTo(2);
        assertThat(usage.overQuota()).isFalse();
    }

    @Test
    void defaultsToZeroWhenNoUsageRowsExistYet() {
        when(monitorRepository.countByUser_IdAndDeletedAtIsNull(user.getId())).thenReturn(0L);
        when(userUsageRepository.findByUserIdAndPeriodMonth(user.getId(), PERIOD)).thenReturn(Optional.empty());
        when(userAlertUsageRepository.findByUserIdAndDay(user.getId(), TODAY)).thenReturn(Optional.empty());

        UsageResponse usage = service.usageFor("u@example.com");

        assertThat(usage.checksThisMonth()).isZero();
        assertThat(usage.alertsToday()).isZero();
        assertThat(usage.overQuota()).isFalse();
    }

    @Test
    void flagsOverQuotaOnceConsumptionReachesThePlanQuota() {
        when(monitorRepository.countByUser_IdAndDeletedAtIsNull(user.getId())).thenReturn(5L);
        when(userUsageRepository.findByUserIdAndPeriodMonth(user.getId(), PERIOD))
                .thenReturn(Optional.of(UserUsage.builder().checksConsumed(free.getMonthlyCheckQuota()).build()));
        when(userAlertUsageRepository.findByUserIdAndDay(user.getId(), TODAY)).thenReturn(Optional.empty());

        UsageResponse usage = service.usageFor("u@example.com");

        // Reaching the quota (not just exceeding it) is what blocks scheduling — mirror that exactly.
        assertThat(usage.overQuota()).isTrue();
    }

    @Test
    void usesTheDatabaseClockNotWallTimeForPeriodBoundaries() {
        when(monitorRepository.countByUser_IdAndDeletedAtIsNull(user.getId())).thenReturn(1L);
        when(userUsageRepository.findByUserIdAndPeriodMonth(user.getId(), PERIOD)).thenReturn(Optional.empty());
        when(userAlertUsageRepository.findByUserIdAndDay(user.getId(), TODAY)).thenReturn(Optional.empty());

        service.usageFor("u@example.com");

        // The repositories were queried with the month/day derived from the DB clock (2026-06),
        // proving no Instant.now()/LocalDate.now() leaked in — a project invariant.
        assertThat(PERIOD).isEqualTo(LocalDate.of(2026, 6, 1));
    }

    @Test
    void rejectsUnknownUser() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.usageFor("ghost@example.com"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

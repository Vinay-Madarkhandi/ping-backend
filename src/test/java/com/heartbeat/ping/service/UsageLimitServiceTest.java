package com.heartbeat.ping.service;

import com.heartbeat.ping.modles.Plan;
import com.heartbeat.ping.modles.User;
import com.heartbeat.ping.repository.MonitorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsageLimitServiceTest {

    @Mock
    private MonitorRepository monitorRepository;
    @Mock
    private PlanService planService;

    private UsageLimitService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new UsageLimitService(monitorRepository, planService);

        Plan free = Plan.builder()
                .id(UUID.randomUUID())
                .name("FREE")
                .maxMonitors(5)
                .minIntervalMs(300_000)
                .maxTimeoutMs(5_000)
                .build();

        user = User.builder().email("u@example.com").plan(free).build();
        user.setId(UUID.randomUUID());

        lenient().when(planService.getById(free.getId())).thenReturn(free);
    }

    @Test
    void allowsMonitorWithinPlanLimits() {
        when(monitorRepository.countByUser_IdAndDeletedAtIsNull(user.getId())).thenReturn(2L);

        assertThatCode(() -> service.validateNewMonitor(user, 300_000, 5_000))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsWhenMonitorCountAtLimit() {
        when(monitorRepository.countByUser_IdAndDeletedAtIsNull(user.getId())).thenReturn(5L);

        assertThatThrownBy(() -> service.validateNewMonitor(user, 300_000, 5_000))
                .isInstanceOf(PlanLimitExceededException.class)
                .hasMessageContaining("Monitor limit");
    }

    @Test
    void rejectsIntervalBelowPlanMinimum() {
        lenient().when(monitorRepository.countByUser_IdAndDeletedAtIsNull(user.getId())).thenReturn(0L);

        assertThatThrownBy(() -> service.validateNewMonitor(user, 30_000, 5_000))
                .isInstanceOf(PlanLimitExceededException.class)
                .hasMessageContaining("interval");
    }

    @Test
    void rejectsTimeoutAbovePlanMaximum() {
        lenient().when(monitorRepository.countByUser_IdAndDeletedAtIsNull(user.getId())).thenReturn(0L);

        assertThatThrownBy(() -> service.validateNewMonitor(user, 300_000, 60_000))
                .isInstanceOf(PlanLimitExceededException.class)
                .hasMessageContaining("Timeout");
    }

    @Test
    void proPlanAllowsWhatFreeRejects() {
        Plan pro = Plan.builder()
                .id(UUID.randomUUID())
                .name("PRO")
                .maxMonitors(50)
                .minIntervalMs(60_000)
                .maxTimeoutMs(10_000)
                .build();
        User proUser = User.builder().email("p@example.com").plan(pro).build();
        proUser.setId(UUID.randomUUID());
        when(planService.getById(pro.getId())).thenReturn(pro);
        when(monitorRepository.countByUser_IdAndDeletedAtIsNull(proUser.getId())).thenReturn(6L);

        // 6 monitors, 1-min interval, 10s timeout — all rejected on FREE, allowed on PRO.
        assertThatCode(() -> service.validateNewMonitor(proUser, 60_000, 10_000))
                .doesNotThrowAnyException();
    }
}

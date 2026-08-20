package com.heartbeat.ping.service;

import com.heartbeat.ping.dto.auth.MeResponse;
import com.heartbeat.ping.modles.Plan;
import com.heartbeat.ping.modles.SubscriptionStatus;
import com.heartbeat.ping.modles.User;
import com.heartbeat.ping.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PlanService planService;
    @Mock private EmailVerificationService emailVerificationService;

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(userRepository, new BCryptPasswordEncoder(), planService, emailVerificationService);
    }

    @Test
    void meReturnsProfilePlanAndSubscription() {
        Plan pro = Plan.builder()
                .id(UUID.randomUUID()).name("PRO")
                .maxMonitors(50).minIntervalMs(60_000).maxTimeoutMs(10_000)
                .monthlyCheckQuota(1_000_000).retentionDays(90)
                .alertCooldownSeconds(900).maxAlertsPerDay(500)
                .priceAmount(49_900).currency("INR").durationDays(30)
                .build();
        User user = User.builder()
                .userName("vinay").email("u@example.com").plan(pro)
                .subscriptionStatus(SubscriptionStatus.ACTIVE)
                .subscriptionEndAt(Instant.parse("2026-07-15T00:00:00Z"))
                .build();
        user.setId(UUID.randomUUID());

        when(userRepository.findByEmail("u@example.com")).thenReturn(Optional.of(user));
        when(planService.getById(pro.getId())).thenReturn(pro);

        MeResponse me = service.me("u@example.com");

        assertThat(me.email()).isEqualTo("u@example.com");
        assertThat(me.subscriptionStatus()).isEqualTo("ACTIVE");
        assertThat(me.subscriptionEndAt()).isEqualTo(Instant.parse("2026-07-15T00:00:00Z"));
        assertThat(me.plan().name()).isEqualTo("PRO");
        assertThat(me.plan().maxMonitors()).isEqualTo(50);
        assertThat(me.plan().priceAmount()).isEqualTo(49_900L);
        assertThat(me.plan().currency()).isEqualTo("INR");
    }
}

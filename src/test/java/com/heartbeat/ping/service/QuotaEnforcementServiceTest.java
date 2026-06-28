package com.heartbeat.ping.service;

import com.heartbeat.ping.repository.MonitorRepository;
import com.heartbeat.ping.repository.UserUsageRepository;
import com.heartbeat.ping.service.metrics.MetricsService;
import com.heartbeat.ping.service.time.DatabaseClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuotaEnforcementServiceTest {

    @Mock
    private UserUsageRepository userUsageRepository;
    @Mock
    private MonitorRepository monitorRepository;
    @Mock
    private MetricsService metricsService;
    @Mock
    private DatabaseClock clock;

    private QuotaEnforcementService service;

    @BeforeEach
    void setUp() {
        service = new QuotaEnforcementService(userUsageRepository, monitorRepository, metricsService, clock);
        when(clock.now()).thenReturn(Instant.parse("2026-06-15T00:00:00Z"));
    }

    @Test
    void blocksOverQuotaUsersAndUnblocksOthers() {
        List<UUID> overQuota = List.of(UUID.randomUUID());
        when(userUsageRepository.findOverQuotaUserIds(any())).thenReturn(overQuota);
        when(monitorRepository.blockForUsers(overQuota)).thenReturn(3);
        when(monitorRepository.countByQuotaBlockedTrue()).thenReturn(3L);

        service.enforce();

        verify(monitorRepository).blockForUsers(overQuota);
        verify(monitorRepository).unblockExceptUsers(overQuota);
        verify(monitorRepository, never()).unblockAll();
        verify(metricsService).setQuotaBlockedMonitors(3L);
    }

    @Test
    void unblocksEveryoneWhenNoUserOverQuota() {
        when(userUsageRepository.findOverQuotaUserIds(any())).thenReturn(List.of());
        when(monitorRepository.countByQuotaBlockedTrue()).thenReturn(0L);

        service.enforce();

        verify(monitorRepository).unblockAll();
        verify(monitorRepository, never()).blockForUsers(anyList());
        verify(metricsService).setQuotaBlockedMonitors(0L);
    }
}

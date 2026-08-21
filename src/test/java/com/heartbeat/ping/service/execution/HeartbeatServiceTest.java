package com.heartbeat.ping.service.execution;

import com.heartbeat.ping.dto.monitor.HeartbeatPingResponse;
import com.heartbeat.ping.modles.Monitor;
import com.heartbeat.ping.modles.MonitorKind;
import com.heartbeat.ping.repository.MonitorRepository;
import com.heartbeat.ping.service.ResourceNotFoundException;
import com.heartbeat.ping.service.check.CheckResult;
import com.heartbeat.ping.service.time.DatabaseClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HeartbeatServiceTest {

    @Mock private MonitorRepository monitorRepository;
    @Mock private MonitorCheckTransactionService transactionService;
    @Mock private DatabaseClock clock;

    private HeartbeatService service;

    @BeforeEach
    void setUp() {
        service = new HeartbeatService(monitorRepository, transactionService, clock);
    }

    @Test
    void unknownTokenThrowsNotFound() {
        when(monitorRepository.findByHeartbeatTokenAndDeletedAtIsNull("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.recordPing("nope"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void httpKindMonitorSharingATokenFieldValueIsNeverMatched() {
        // findByHeartbeatTokenAndDeletedAtIsNull only ever returns HEARTBEAT monitors in practice
        // (HTTP monitors never get a token), but the service still defends the invariant explicitly.
        Monitor httpMonitor = heartbeatMonitor();
        httpMonitor.setKind(MonitorKind.HTTP);
        when(monitorRepository.findByHeartbeatTokenAndDeletedAtIsNull("tok")).thenReturn(Optional.of(httpMonitor));

        assertThatThrownBy(() -> service.recordPing("tok"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void recordsAPingAndReturnsNextExpectedDeadline() {
        Monitor monitor = heartbeatMonitor();
        Instant now = Instant.parse("2026-08-20T10:00:00Z");
        when(monitorRepository.findByHeartbeatTokenAndDeletedAtIsNull("tok")).thenReturn(Optional.of(monitor));
        when(clock.now()).thenReturn(now);

        HeartbeatPingResponse response = service.recordPing("tok");

        verify(transactionService).recordResult(eq(monitor.getId()), any(), eq(CheckResult.up(200, 0)));
        assertThat(response.monitor()).isEqualTo("nightly-backup");
        // interval (5min) + grace (1min)
        assertThat(response.nextExpectedBy()).isEqualTo(now.plusSeconds(360));
    }

    @Test
    void rapidRepeatedPingsAreDebouncedToASingleRecordedResult() {
        Monitor monitor = heartbeatMonitor();
        when(monitorRepository.findByHeartbeatTokenAndDeletedAtIsNull("tok")).thenReturn(Optional.of(monitor));
        when(clock.now())
                .thenReturn(Instant.parse("2026-08-20T10:00:00.000Z"))
                .thenReturn(Instant.parse("2026-08-20T10:00:00.500Z")); // 500ms later — inside debounce window

        service.recordPing("tok");
        service.recordPing("tok");

        verify(transactionService, times(1)).recordResult(any(), any(), any());
    }

    private Monitor heartbeatMonitor() {
        Monitor monitor = Monitor.builder()
                .name("nightly-backup")
                .kind(MonitorKind.HEARTBEAT)
                .heartbeatToken("tok")
                .intervalMilliseconds(300_000) // 5 min
                .gracePeriodMilliseconds(60_000) // 1 min
                .timeoutMilliseconds(5_000)
                .build();
        monitor.setId(UUID.randomUUID());
        return monitor;
    }
}

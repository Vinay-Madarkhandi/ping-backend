package com.heartbeat.ping.service.alert;

import com.heartbeat.ping.config.properties.AlertProperties;
import com.heartbeat.ping.modles.Incident;
import com.heartbeat.ping.modles.Monitor;
import com.heartbeat.ping.modles.MonitorState;
import com.heartbeat.ping.modles.MonitorStatus;
import com.heartbeat.ping.modles.User;
import com.heartbeat.ping.service.check.CheckResult;
import com.heartbeat.ping.service.incident.IncidentService;
import com.heartbeat.ping.service.notification.AlertType;
import com.heartbeat.ping.service.notification.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultAlertEngineTest {

    @Mock
    private IncidentService incidentService;
    @Mock
    private NotificationService notificationService;

    private AlertProperties alertProps;
    private DefaultAlertEngine engine;

    private Monitor monitor;
    private MonitorStatus status;

    @BeforeEach
    void setUp() {
        alertProps = new AlertProperties();
        alertProps.setFailureThreshold(3);
        alertProps.setRecoveryThreshold(1);
        engine = new DefaultAlertEngine(alertProps, incidentService, notificationService);

        UUID monitorId = UUID.randomUUID();
        monitor = Monitor.builder().name("api").url("https://x").build();
        monitor.setId(monitorId);
        monitor.setUser(User.builder().email("u@example.com").build());

        status = new MonitorStatus();
        status.setMonitor(monitor);
        status.setCurrentState(MonitorState.UNKNOWN);
        status.setUptimePercentage(100.0);
    }

    private void fail() {
        engine.process(monitor, status, CheckResult.down(500, 10, "boom"), Instant.now());
    }

    private void succeed() {
        engine.process(monitor, status, CheckResult.up(200, 10), Instant.now());
    }

    @Test
    void staysSuspectBelowThresholdWithNoAlert() {
        fail();
        fail();

        assertThat(status.getCurrentState()).isEqualTo(MonitorState.SUSPECT);
        assertThat(status.getConsecutiveFailures()).isEqualTo(2);
        verifyNoInteractions(incidentService, notificationService);
    }

    @Test
    void opensIncidentAndAlertsOnThirdFailure() {
        when(incidentService.open(any(), any(), any())).thenReturn(incidentWithId());

        fail();
        fail();
        fail();

        assertThat(status.getCurrentState()).isEqualTo(MonitorState.DOWN);
        assertThat(status.getDownSince()).isNotNull();
        verify(incidentService).open(eq(monitor), any(), any());
        verify(notificationService).enqueue(eq(monitor), any(), eq(AlertType.DOWN), any());
    }

    @Test
    void doesNotReopenOrRealertWhileAlreadyDown() {
        when(incidentService.open(any(), any(), any())).thenReturn(incidentWithId());

        fail();
        fail();
        fail(); // -> DOWN, 1 incident, 1 alert
        fail(); // still DOWN

        verify(incidentService).open(any(), any(), any());        // exactly once
        verify(notificationService).enqueue(any(), any(), eq(AlertType.DOWN), any()); // exactly once
    }

    @Test
    void resolvesIncidentAndSendsRecoveryOnSuccessAfterDown() {
        Incident incident = incidentWithId();
        when(incidentService.open(any(), any(), any())).thenReturn(incident);
        when(incidentService.resolve(eq(monitor.getId()), any())).thenReturn(Optional.of(incident));

        fail();
        fail();
        fail();   // DOWN
        succeed(); // RECOVERY

        assertThat(status.getCurrentState()).isEqualTo(MonitorState.UP);
        assertThat(status.getDownSince()).isNull();
        verify(incidentService).resolve(eq(monitor.getId()), any());
        verify(notificationService).enqueue(eq(monitor), any(), eq(AlertType.RECOVERY), any());
    }

    @Test
    void inconclusiveResultNeverChangesStateOrOpensIncident() {
        fail();
        fail(); // SUSPECT with 2 failures

        engine.process(monitor, status, CheckResult.inconclusive(10, "pool exhausted"), Instant.now());

        assertThat(status.getCurrentState()).isEqualTo(MonitorState.SUSPECT);
        assertThat(status.getConsecutiveFailures()).isEqualTo(2); // unchanged by inconclusive
        verifyNoInteractions(incidentService, notificationService);
    }

    @Test
    void pausedMonitorIsNotEvaluatedByCaller() {
        // The engine itself assumes the caller skips paused monitors; verify a plain success path
        // does not alert so we don't accidentally notify on recovery from UNKNOWN.
        succeed();
        assertThat(status.getCurrentState()).isEqualTo(MonitorState.UP);
        verify(notificationService, never()).enqueue(any(), any(), any(), any());
    }

    private Incident incidentWithId() {
        Incident incident = Incident.builder().monitor(monitor).build();
        incident.setId(UUID.randomUUID());
        return incident;
    }
}

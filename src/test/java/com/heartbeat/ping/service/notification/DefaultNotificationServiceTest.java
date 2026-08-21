package com.heartbeat.ping.service.notification;

import com.heartbeat.ping.config.properties.EmailProperties;
import com.heartbeat.ping.config.properties.WebhookProperties;
import com.heartbeat.ping.modles.AlertChannel;
import com.heartbeat.ping.modles.AlertChannelType;
import com.heartbeat.ping.modles.EmailOutbox;
import com.heartbeat.ping.modles.Incident;
import com.heartbeat.ping.modles.IncidentStatus;
import com.heartbeat.ping.modles.Monitor;
import com.heartbeat.ping.modles.User;
import com.heartbeat.ping.modles.WebhookOutbox;
import com.heartbeat.ping.repository.EmailOutboxRepository;
import com.heartbeat.ping.repository.WebhookOutboxRepository;
import com.heartbeat.ping.service.metrics.MetricsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultNotificationServiceTest {

    @Mock
    private EmailOutboxRepository outboxRepository;

    @Mock
    private MetricsService metricsService;

    @Mock
    private WebhookOutboxRepository webhookOutboxRepository;

    @Mock
    private WebhookPayloadBuilder webhookPayloadBuilder;

    private final EmailProperties emailProps = new EmailProperties();
    private final WebhookProperties webhookProps = new WebhookProperties();

    private DefaultNotificationService service() {
        return new DefaultNotificationService(
                outboxRepository, emailProps, metricsService, webhookOutboxRepository, webhookProps, webhookPayloadBuilder);
    }

    private Monitor monitorWithUser(String email) {
        User user = User.builder().email(email).build();
        Monitor monitor = Monitor.builder().name("api").url("https://api.example.com").user(user).build();
        monitor.setId(UUID.randomUUID());
        return monitor;
    }

    private AlertChannel channel(AlertChannelType type, boolean active) {
        AlertChannel channel = AlertChannel.builder()
                .type(type)
                .name("my-channel")
                .targetUrl("https://hooks.example.com/abc")
                .active(active)
                .build();
        channel.setId(UUID.randomUUID());
        return channel;
    }

    private Incident incidentWithId() {
        Incident incident = Incident.builder()
                .startedAt(Instant.now())
                .failureReason("connect timeout")
                .status(IncidentStatus.OPEN)
                .build();
        incident.setId(UUID.randomUUID());
        return incident;
    }

    @Test
    void enqueuesSslExpiryWarningWithStableDedupeKey() {
        Monitor monitor = monitorWithUser("owner@example.com");
        Instant expiresAt = Instant.now().plusSeconds(86400 * 10);
        when(outboxRepository.existsByDedupeKey("ssl-expiry:" + monitor.getId() + ":" + expiresAt.toEpochMilli()))
                .thenReturn(false);

        service().enqueueSslExpiryWarning(monitor, expiresAt, 10, Instant.now());

        ArgumentCaptor<EmailOutbox> captor = ArgumentCaptor.forClass(EmailOutbox.class);
        verify(outboxRepository).save(captor.capture());
        EmailOutbox saved = captor.getValue();
        assertEquals("owner@example.com", saved.getRecipient());
        assertEquals("ssl-expiry:" + monitor.getId() + ":" + expiresAt.toEpochMilli(), saved.getDedupeKey());
        assertTrue(saved.getSubject().contains("10 days"));
    }

    @Test
    void skipsWhenAlreadyWarnedForThisCertificate() {
        Monitor monitor = monitorWithUser("owner@example.com");
        Instant expiresAt = Instant.now().plusSeconds(86400 * 5);
        when(outboxRepository.existsByDedupeKey("ssl-expiry:" + monitor.getId() + ":" + expiresAt.toEpochMilli()))
                .thenReturn(true);

        service().enqueueSslExpiryWarning(monitor, expiresAt, 5, Instant.now());

        verify(outboxRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void skipsWhenMonitorHasNoOwnerEmail() {
        Monitor monitor = Monitor.builder().name("api").url("https://api.example.com").build();
        monitor.setId(UUID.randomUUID());

        service().enqueueSslExpiryWarning(monitor, Instant.now().plusSeconds(86400), 1, Instant.now());

        verify(outboxRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void enqueueFansOutToActiveChannelsAlongsideEmail() {
        Monitor monitor = monitorWithUser("owner@example.com");
        AlertChannel active = channel(AlertChannelType.SLACK, true);
        monitor.setAlertChannels(new LinkedHashSet<>(Set.of(active)));
        Incident incident = incidentWithId();
        Instant now = Instant.now();

        when(outboxRepository.existsByDedupeKey(any())).thenReturn(false);
        when(webhookOutboxRepository.existsByDedupeKey(any())).thenReturn(false);
        when(webhookPayloadBuilder.build(AlertChannelType.SLACK, monitor, incident, AlertType.DOWN))
                .thenReturn("{\"text\":\"down\"}");

        service().enqueue(monitor, incident, AlertType.DOWN, now);

        verify(outboxRepository).save(any(EmailOutbox.class));

        ArgumentCaptor<WebhookOutbox> captor = ArgumentCaptor.forClass(WebhookOutbox.class);
        verify(webhookOutboxRepository).save(captor.capture());
        WebhookOutbox saved = captor.getValue();
        assertEquals(active.getTargetUrl(), saved.getTargetUrl());
        assertEquals(AlertChannelType.SLACK, saved.getChannelType());
        assertEquals(incident.getId() + ":DOWN:" + active.getId(), saved.getDedupeKey());
        assertEquals("{\"text\":\"down\"}", saved.getPayload());
    }

    @Test
    void enqueueSkipsInactiveChannels() {
        Monitor monitor = monitorWithUser("owner@example.com");
        monitor.setAlertChannels(new LinkedHashSet<>(Set.of(channel(AlertChannelType.WEBHOOK, false))));
        Incident incident = incidentWithId();

        when(outboxRepository.existsByDedupeKey(any())).thenReturn(false);

        service().enqueue(monitor, incident, AlertType.DOWN, Instant.now());

        verify(webhookOutboxRepository, never()).save(any());
    }

    @Test
    void enqueueSkipsChannelAlreadyNotifiedForThisTransition() {
        Monitor monitor = monitorWithUser("owner@example.com");
        AlertChannel active = channel(AlertChannelType.DISCORD, true);
        monitor.setAlertChannels(new LinkedHashSet<>(Set.of(active)));
        Incident incident = incidentWithId();

        when(outboxRepository.existsByDedupeKey(any())).thenReturn(false);
        when(webhookOutboxRepository.existsByDedupeKey(incident.getId() + ":DOWN:" + active.getId()))
                .thenReturn(true);

        service().enqueue(monitor, incident, AlertType.DOWN, Instant.now());

        verify(webhookOutboxRepository, never()).save(any());
    }
}

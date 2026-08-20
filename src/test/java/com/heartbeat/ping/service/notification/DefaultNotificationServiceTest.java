package com.heartbeat.ping.service.notification;

import com.heartbeat.ping.config.properties.EmailProperties;
import com.heartbeat.ping.modles.EmailOutbox;
import com.heartbeat.ping.modles.Monitor;
import com.heartbeat.ping.modles.User;
import com.heartbeat.ping.repository.EmailOutboxRepository;
import com.heartbeat.ping.service.metrics.MetricsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultNotificationServiceTest {

    @Mock
    private EmailOutboxRepository outboxRepository;

    @Mock
    private MetricsService metricsService;

    private final EmailProperties emailProps = new EmailProperties();

    private DefaultNotificationService service() {
        return new DefaultNotificationService(outboxRepository, emailProps, metricsService);
    }

    private Monitor monitorWithUser(String email) {
        User user = User.builder().email(email).build();
        Monitor monitor = Monitor.builder().name("api").url("https://api.example.com").user(user).build();
        monitor.setId(UUID.randomUUID());
        return monitor;
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
}

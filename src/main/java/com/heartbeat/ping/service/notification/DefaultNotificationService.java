package com.heartbeat.ping.service.notification;

import com.heartbeat.ping.config.properties.EmailProperties;
import com.heartbeat.ping.modles.EmailOutbox;
import com.heartbeat.ping.modles.EmailStatus;
import com.heartbeat.ping.modles.Incident;
import com.heartbeat.ping.modles.Monitor;
import com.heartbeat.ping.modles.User;
import com.heartbeat.ping.repository.EmailOutboxRepository;
import com.heartbeat.ping.service.metrics.MetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Locale;

/**
 * Enqueues alert emails into the outbox. The dedupe key {@code <incidentId>:<type>} plus the
 * UNIQUE constraint make this idempotent: a concurrent or replayed transition enqueues at most once.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultNotificationService implements NotificationService {

    private final EmailOutboxRepository outboxRepository;
    private final EmailProperties emailProps;
    private final MetricsService metricsService;

    @Override
    public void enqueue(Monitor monitor, Incident incident, AlertType type, Instant now) {
        String recipient = recipientFor(monitor);
        if (recipient == null) {
            log.warn("No recipient for monitor {}; skipping {} alert", monitor.getId(), type);
            return;
        }

        String dedupeKey = incident.getId() + ":" + type.name();
        if (outboxRepository.existsByDedupeKey(dedupeKey)) {
            return; // already enqueued for this transition
        }

        EmailOutbox email = EmailOutbox.builder()
                .monitorId(monitor.getId())
                .recipient(recipient)
                .subject(subjectFor(monitor, type))
                .body(bodyFor(monitor, incident, type))
                .status(EmailStatus.PENDING)
                .attempts(0)
                .maxAttempts(emailProps.getMaxAttempts())
                .nextAttemptAt(now)
                .dedupeKey(dedupeKey)
                .createdAt(now)
                .build();

        try {
            outboxRepository.save(email);
            metricsService.recordAlert(type.name().toLowerCase(Locale.ROOT));
        } catch (DataIntegrityViolationException duplicate) {
            // Lost the race on the unique dedupe key — another transition already enqueued it.
            log.debug("Alert {} for incident {} already enqueued", type, incident.getId());
        }
    }

    private String recipientFor(Monitor monitor) {
        User user = monitor.getUser();
        return user != null ? user.getEmail() : null;
    }

    private String subjectFor(Monitor monitor, AlertType type) {
        return switch (type) {
            case DOWN -> "[ALERT] " + monitor.getName() + " is DOWN";
            case RECOVERY -> "[RECOVERED] " + monitor.getName() + " is back UP";
        };
    }

    private String bodyFor(Monitor monitor, Incident incident, AlertType type) {
        return switch (type) {
            case DOWN -> """
                    Monitor "%s" (%s) is DOWN.
                    Since: %s
                    Reason: %s
                    """.formatted(monitor.getName(), monitor.getUrl(), incident.getStartedAt(),
                    incident.getFailureReason() == null ? "unknown" : incident.getFailureReason());
            case RECOVERY -> """
                    Monitor "%s" (%s) has RECOVERED.
                    Down from: %s
                    Recovered at: %s
                    Downtime: %s seconds
                    """.formatted(monitor.getName(), monitor.getUrl(), incident.getStartedAt(),
                    incident.getResolvedAt(), incident.getDurationSeconds());
        };
    }
}

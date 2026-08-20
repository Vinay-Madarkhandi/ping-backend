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

    /**
     * Enqueue a password reset email. Uses the same durable outbox as alerts, so delivery is
     * eventually guaranteed and failures are visible in the alert history UI.
     */
    public void sendPasswordResetEmail(String recipient, String userName, String resetLink) {
        String dedupeKey = "password-reset:" + recipient + ":" + Instant.now().toEpochMilli();

        EmailOutbox email = EmailOutbox.builder()
                .monitorId(null) // not associated with a monitor
                .recipient(recipient)
                .subject("Password Reset Request")
                .body("""
                        Hi %s,
                        
                        You requested to reset your password. Click the link below to set a new password:
                        
                        %s
                        
                        This link expires in 1 hour and can only be used once.
                        
                        If you did not request this reset, you can safely ignore this email.
                        
                        - Ping Me Heart Team
                        """.formatted(userName, resetLink))
                .status(EmailStatus.PENDING)
                .attempts(0)
                .maxAttempts(emailProps.getMaxAttempts())
                .nextAttemptAt(Instant.now())
                .dedupeKey(dedupeKey)
                .createdAt(Instant.now())
                .build();

        outboxRepository.save(email);
        log.info("Password reset email enqueued for {}", recipient);
    }

    /**
     * Enqueue an email verification email. Critical for preventing spam through the alert system
     * since unverified emails can be used to send alerts to arbitrary addresses.
     */
    public void sendEmailVerificationEmail(String recipient, String userName, String verifyLink) {
        String dedupeKey = "email-verify:" + recipient + ":" + Instant.now().toEpochMilli();

        EmailOutbox email = EmailOutbox.builder()
                .monitorId(null)
                .recipient(recipient)
                .subject("Verify Your Email Address")
                .body("""
                        Hi %s,
                        
                        Welcome to Ping Me Heart! Please verify your email address by clicking the link below:
                        
                        %s
                        
                        This link expires in 24 hours.
                        
                        Until your email is verified, alerts will not be sent to prevent spam.
                        
                        - Ping Me Heart Team
                        """.formatted(userName, verifyLink))
                .status(EmailStatus.PENDING)
                .attempts(0)
                .maxAttempts(emailProps.getMaxAttempts())
                .nextAttemptAt(Instant.now())
                .dedupeKey(dedupeKey)
                .createdAt(Instant.now())
                .build();

        outboxRepository.save(email);
        log.info("Email verification email enqueued for {}", recipient);
    }
}

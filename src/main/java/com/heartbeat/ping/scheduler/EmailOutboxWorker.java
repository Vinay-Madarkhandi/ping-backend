package com.heartbeat.ping.scheduler;

import com.heartbeat.ping.config.properties.EmailProperties;
import com.heartbeat.ping.modles.EmailStatus;
import com.heartbeat.ping.service.EmailNotificationService;
import com.heartbeat.ping.service.metrics.MetricsService;
import com.heartbeat.ping.service.notification.EmailOutboxTransactionService;
import com.heartbeat.ping.service.notification.SendableEmail;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Drains the durable email outbox: claims due rows (leased so a crash retries), sends each via SMTP
 * outside any transaction, then marks SENT or schedules an exponential-backoff retry (FAILED after
 * max attempts). Shares the dedicated scheduling pool and is disabled when the scheduler is off.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "monitor.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class EmailOutboxWorker {

    private final EmailOutboxTransactionService transactionService;
    private final EmailNotificationService emailSender;
    private final EmailProperties emailProps;
    private final MetricsService metricsService;

    @Scheduled(fixedDelayString = "${monitor.email.worker-rate-ms:30000}")
    public void drain() {
        try {
            List<SendableEmail> batch = transactionService.claimSendable(emailProps.getBatchSize());
            for (SendableEmail email : batch) {
                send(email);
            }
        } catch (Exception e) {
            log.error("Email outbox drain failed; will retry next tick", e);
        }
    }

    private void send(SendableEmail email) {
        try {
            emailSender.sendEmail(email.recipient(), email.subject(), email.body());
            transactionService.markSent(email.id());
            metricsService.recordEmail("sent");
        } catch (Exception ex) {
            EmailStatus outcome = transactionService.markFailure(email.id(), ex.getMessage());
            metricsService.recordEmail(outcome == EmailStatus.FAILED ? "failed" : "retry");
            log.warn("Email {} send failed (now {}): {}", email.id(), outcome, ex.getMessage());
        }
    }
}

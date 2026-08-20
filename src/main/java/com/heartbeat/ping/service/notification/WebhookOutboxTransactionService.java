package com.heartbeat.ping.service.notification;

import com.heartbeat.ping.config.properties.WebhookProperties;
import com.heartbeat.ping.modles.WebhookOutbox;
import com.heartbeat.ping.modles.WebhookStatus;
import com.heartbeat.ping.repository.WebhookOutboxRepository;
import com.heartbeat.ping.service.time.DatabaseClock;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Transactional boundaries for the webhook outbox worker, mirroring
 * {@link EmailOutboxTransactionService} exactly: claiming leases rows forward so a crash mid-send
 * just retries, and the HTTP POST itself never holds a row lock or DB connection.
 */
@Service
@RequiredArgsConstructor
public class WebhookOutboxTransactionService {

    private final WebhookOutboxRepository outboxRepository;
    private final WebhookProperties webhookProps;
    private final DatabaseClock clock;

    @Transactional
    public List<SendableWebhook> claimSendable(int batchSize) {
        Instant now = clock.now();
        List<WebhookOutbox> rows = outboxRepository.claimSendable(
                WebhookStatus.PENDING, now, PageRequest.of(0, batchSize));

        return rows.stream()
                .map(webhook -> {
                    webhook.setNextAttemptAt(now.plus(backoff(webhook.getAttempts())));
                    return new SendableWebhook(
                            webhook.getId(), webhook.getTargetUrl(), webhook.getChannelType(), webhook.getPayload());
                })
                .toList();
    }

    @Transactional
    public void markSent(UUID id) {
        outboxRepository.findById(id).ifPresent(webhook -> {
            webhook.setStatus(WebhookStatus.SENT);
            webhook.setSentAt(clock.now());
        });
    }

    /** Records a failed attempt; moves to FAILED (dead-letter) once max attempts are exhausted. */
    @Transactional
    public WebhookStatus markFailure(UUID id, String error) {
        WebhookOutbox webhook = outboxRepository.findById(id).orElse(null);
        if (webhook == null) {
            return WebhookStatus.FAILED;
        }
        webhook.setAttempts(webhook.getAttempts() + 1);
        webhook.setLastError(error);
        if (webhook.getAttempts() >= webhook.getMaxAttempts()) {
            webhook.setStatus(WebhookStatus.FAILED);
        }
        return webhook.getStatus();
    }

    /** Exponential backoff from the base delay: base * 2^attempts. */
    private Duration backoff(int attempts) {
        long factor = 1L << Math.min(attempts, 16); // cap the shift to avoid overflow
        return webhookProps.getBackoff().multipliedBy(factor);
    }
}

package com.heartbeat.ping.scheduler;

import com.heartbeat.ping.config.properties.WebhookProperties;
import com.heartbeat.ping.modles.WebhookStatus;
import com.heartbeat.ping.service.notification.SendableWebhook;
import com.heartbeat.ping.service.notification.WebhookDeliveryService;
import com.heartbeat.ping.service.notification.WebhookOutboxTransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Drains the durable webhook outbox: claims due rows (leased so a crash retries), POSTs each
 * outside any transaction, then marks SENT or schedules an exponential-backoff retry (FAILED after
 * max attempts). Mirrors {@link EmailOutboxWorker} exactly, one outbox per delivery mechanism.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "monitor.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class WebhookOutboxWorker {

    private final WebhookOutboxTransactionService transactionService;
    private final WebhookDeliveryService deliveryService;
    private final WebhookProperties webhookProps;

    @Scheduled(fixedDelayString = "${monitor.webhook.worker-rate-ms:15000}")
    public void drain() {
        try {
            List<SendableWebhook> batch = transactionService.claimSendable(webhookProps.getBatchSize());
            for (SendableWebhook webhook : batch) {
                send(webhook);
            }
        } catch (Exception e) {
            log.error("Webhook outbox drain failed; will retry next tick", e);
        }
    }

    private void send(SendableWebhook webhook) {
        try {
            deliveryService.deliver(webhook.targetUrl(), webhook.payload());
            transactionService.markSent(webhook.id());
        } catch (Exception ex) {
            WebhookStatus outcome = transactionService.markFailure(webhook.id(), ex.getMessage());
            log.warn("Webhook {} delivery failed (now {}): {}", webhook.id(), outcome, ex.getMessage());
        }
    }
}

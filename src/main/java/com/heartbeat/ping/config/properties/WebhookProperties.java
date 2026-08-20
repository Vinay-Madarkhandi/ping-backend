package com.heartbeat.ping.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Durable webhook outbox worker settings: retry, backoff and drain cadence ({@code monitor.webhook.*}). */
@Getter
@Setter
@ConfigurationProperties(prefix = "monitor.webhook")
public class WebhookProperties {

    private int maxAttempts = 5;

    /** Base delay for exponential backoff between send attempts. */
    private Duration backoff = Duration.ofSeconds(30);

    /** How often the outbox worker drains pending webhooks. */
    private long workerRateMs = 15_000;

    private int batchSize = 50;

    /** Connect/response timeout for the outbound delivery request itself. */
    private Duration requestTimeout = Duration.ofSeconds(10);
}

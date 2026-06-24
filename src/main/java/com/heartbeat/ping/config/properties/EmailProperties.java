package com.heartbeat.ping.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Durable email outbox worker settings: retry, backoff and drain cadence ({@code monitor.email.*}). */
@Getter
@Setter
@ConfigurationProperties(prefix = "monitor.email")
public class EmailProperties {

    private int maxAttempts = 5;

    /** Base delay for exponential backoff between send attempts. */
    private Duration backoff = Duration.ofMinutes(1);

    /** How often the outbox worker drains pending mail. */
    private long workerRateMs = 30_000;

    private int batchSize = 50;
}

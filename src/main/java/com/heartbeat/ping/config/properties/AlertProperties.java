package com.heartbeat.ping.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Alert-engine thresholds and anti-spam settings ({@code monitor.alert.*}). */
@Getter
@Setter
@ConfigurationProperties(prefix = "monitor.alert")
public class AlertProperties {

    /** Consecutive failures required before a DOWN alert fires. */
    private int failureThreshold = 3;

    /** Consecutive successes required before a DOWN monitor is declared recovered. */
    private int recoveryThreshold = 1;

    /** Minimum gap between repeated DOWN alerts for the same monitor (recovery alerts ignore this). */
    private Duration cooldown = Duration.ofMinutes(15);
}

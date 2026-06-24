package com.heartbeat.ping.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Duration-based uptime calculation settings ({@code monitor.uptime.*}). */
@Getter
@Setter
@ConfigurationProperties(prefix = "monitor.uptime")
public class UptimeProperties {

    /** Default reporting window when the caller does not specify one. */
    private Duration defaultWindow = Duration.ofHours(24);

    /** A gap between consecutive checks longer than {@code gapMultiplier x interval} counts as missing data. */
    private int gapMultiplier = 3;

    /** Floor for the gap threshold so very small intervals don't flag normal jitter as a gap. */
    private Duration minGapThreshold = Duration.ofSeconds(30);
}

package com.heartbeat.ping.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Raw-log retention and rollup scheduling ({@code monitor.retention.*}). */
@Getter
@Setter
@ConfigurationProperties(prefix = "monitor.retention")
public class RetentionProperties {

    /** Raw logs older than this are aggregated then purged. */
    private int days = 30;

    /** Cron for the daily retention/rollup job. */
    private String cron = "0 30 3 * * *";
}

package com.heartbeat.ping.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Connection-pool and timeout settings for the outbound health-check HTTP client ({@code monitor.http.*}). */
@Getter
@Setter
@ConfigurationProperties(prefix = "monitor.http")
public class HttpClientProperties {

    private int maxTotal = 200;
    private int maxPerRoute = 20;
    private Duration connectTimeout = Duration.ofSeconds(5);

    /** Fallback read timeout when a monitor does not specify its own. */
    private Duration responseTimeout = Duration.ofSeconds(10);
}

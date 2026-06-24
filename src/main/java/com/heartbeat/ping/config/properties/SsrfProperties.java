package com.heartbeat.ping.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** SSRF guard toggles ({@code monitor.ssrf.*}). */
@Getter
@Setter
@ConfigurationProperties(prefix = "monitor.ssrf")
public class SsrfProperties {

    private boolean enabled = true;

    /** Escape hatch for local dev/testing against private hosts. */
    private boolean allowPrivate = false;
}

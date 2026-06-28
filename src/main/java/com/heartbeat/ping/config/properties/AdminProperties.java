package com.heartbeat.ping.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Operational admin settings ({@code monitor.admin.*}). There is no role system, so the admin
 * usage endpoint is gated by this email allowlist.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "monitor.admin")
public class AdminProperties {

    /** Emails permitted to access the admin usage endpoint. */
    private List<String> emails = new ArrayList<>();
}

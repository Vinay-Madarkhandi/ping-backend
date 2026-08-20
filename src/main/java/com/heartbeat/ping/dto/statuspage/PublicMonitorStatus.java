package com.heartbeat.ping.dto.statuspage;

/**
 * Public-safe per-monitor projection — deliberately excludes the URL, check configuration, and
 * anything else that isn't the owner's own chosen display name. {@code state} is one of
 * UP / SUSPECT / DOWN / PAUSED / UNKNOWN. {@code uptimePercentage90d} is null only when there is
 * not yet enough history to compute one (e.g. a brand-new monitor).
 */
public record PublicMonitorStatus(String name, String state, Double uptimePercentage90d) {
}

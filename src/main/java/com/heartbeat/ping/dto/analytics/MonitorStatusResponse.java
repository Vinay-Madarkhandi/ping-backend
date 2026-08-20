package com.heartbeat.ping.dto.analytics;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonitorStatusResponse {
    private boolean isUp;
    /** Confirmed health from the alert FSM: UNKNOWN/UP/SUSPECT/DOWN. */
    private String currentState;
    /**
     * What to show the user, by precedence: "QUOTA_EXCEEDED" when the owner is over their monthly
     * check quota (the scheduler skips the monitor), else "PAUSED" when administratively paused,
     * else the health state. Neither condition is an FSM state, so both are derived here.
     */
    private String displayState;
    /** True when the owning user is over quota, so this monitor is not being checked. */
    private boolean quotaBlocked;
    private double uptimePercentage;
    private int totalChecks;
    private int totalUp;
    private int totalDown;
    private LocalDateTime lastDowntimeAt;
    private LocalDateTime lastCheckedAt;
    /** Null for HTTP monitors or before the first successful TLS handshake. */
    private LocalDateTime sslCertExpiresAt;
    /** Convenience for the UI; null whenever sslCertExpiresAt is null. Can be negative if expired. */
    private Long sslDaysRemaining;
}

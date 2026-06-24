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
    /** What to show the user: "PAUSED" when administratively paused, else the health state. */
    private String displayState;
    private double uptimePercentage;
    private int totalChecks;
    private int totalUp;
    private int totalDown;
    private LocalDateTime lastDowntimeAt;
    private LocalDateTime lastCheckedAt;
}

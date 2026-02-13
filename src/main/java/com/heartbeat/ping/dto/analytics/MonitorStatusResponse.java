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
    private double uptimePercentage;
    private int totalChecks;
    private int totalUp;
    private int totalDown;
    private LocalDateTime lastDowntimeAt;
    private LocalDateTime lastCheckedAt;
}

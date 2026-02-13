package com.heartbeat.ping.dto.monitor;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HealthMonitorResponse {
    private String status;
}

package com.heartbeat.ping.dto.monitor;

import com.heartbeat.ping.modles.MonitorMethod;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MonitorListResponseDto {
    private UUID id;
    private String name;
    private String url;
    private boolean active;
    private MonitorMethod method;
    private Instant nextCheckAt;
    private double uptimePercentage;
}

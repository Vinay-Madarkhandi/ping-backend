package com.heartbeat.ping.dto.monitor;

import com.heartbeat.ping.modles.MonitorMethod;
import lombok.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Set;
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

    // --- New fields for the frontend to avoid per-monitor /status calls ---
    private LocalDateTime createdAt;
    private boolean paused;
    private boolean quotaBlocked;
    private String currentState;
    private String displayState;
    private int intervalMilliseconds;
    private int timeoutMilliseconds;
    private Set<String> tags;
}

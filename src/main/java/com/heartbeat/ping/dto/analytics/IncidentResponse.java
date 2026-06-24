package com.heartbeat.ping.dto.analytics;

import com.heartbeat.ping.modles.Incident;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class IncidentResponse {

    private UUID id;
    private Instant startedAt;
    private Instant resolvedAt;
    private Long durationSeconds;
    private String failureReason;
    private String status;

    public static IncidentResponse from(Incident incident) {
        return IncidentResponse.builder()
                .id(incident.getId())
                .startedAt(incident.getStartedAt())
                .resolvedAt(incident.getResolvedAt())
                .durationSeconds(incident.getDurationSeconds())
                .failureReason(incident.getFailureReason())
                .status(incident.getStatus().name())
                .build();
    }
}

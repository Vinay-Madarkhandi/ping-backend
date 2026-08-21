package com.heartbeat.ping.dto.maintenance;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MaintenanceWindowResponse {
    private UUID id;
    private String title;
    private Instant startsAt;
    private Instant endsAt;
    /** True once the worker has actually paused the monitor for this window (start time reached). */
    private boolean active;
    /** True once the window has ended and the monitor (if paused by it) has been resumed. */
    private boolean completed;
    private Instant createdAt;
}

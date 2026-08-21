package com.heartbeat.ping.dto.monitor;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateMonitorResponseDto {

    private String id;

    private String name;

    private String url;

    private boolean isActive;

    private LocalDateTime createdAt;

    private String kind;

    /** The URL to ping. Present only for HEARTBEAT monitors; also available later via the monitor detail DTO. */
    private String heartbeatUrl;

}

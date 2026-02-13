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

}

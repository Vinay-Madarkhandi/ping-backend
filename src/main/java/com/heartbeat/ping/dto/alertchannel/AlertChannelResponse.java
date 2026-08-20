package com.heartbeat.ping.dto.alertchannel;

import com.heartbeat.ping.modles.AlertChannelType;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AlertChannelResponse {
    private UUID id;
    private AlertChannelType type;
    private String name;
    private String targetUrl;
    private boolean active;
    private LocalDateTime createdAt;
}

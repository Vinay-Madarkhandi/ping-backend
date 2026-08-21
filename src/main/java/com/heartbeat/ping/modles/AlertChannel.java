package com.heartbeat.ping.modles;

import jakarta.persistence.*;
import lombok.*;

/**
 * A user-owned outbound alert destination (generic webhook, Slack incoming webhook, or Discord
 * webhook). A monitor opts into zero or more channels via {@code monitor_alert_channel}; email
 * alerts to the owner always send regardless of channel configuration.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "alert_channel")
public class AlertChannel extends BaseModel {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AlertChannelType type;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "target_url", nullable = false, length = 2048)
    private String targetUrl;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;
}

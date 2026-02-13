package com.heartbeat.ping.modles;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "monitor_status")
public class MonitorStatus {
    @Id
    private UUID monitorId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "monitor_id", nullable = false)
    private Monitor monitor;

    private int totalChecks;
    private int totalUp;
    private int totalDown;

    @Column(nullable = false)
    private double uptimePercentage;

    private LocalDateTime lastDowntimeAt;

    private LocalDateTime updatedAt;
}

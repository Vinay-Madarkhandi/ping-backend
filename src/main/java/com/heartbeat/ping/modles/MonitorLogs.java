package com.heartbeat.ping.modles;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "monitor_logs", indexes = {
        @Index(name = "idx_monitorlogs_monitor_time", columnList = "monitor_id, checked_at")
})
public class MonitorLogs {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "monitor_id", nullable = false)
    private Monitor monitor;

    private int statusCode;

    private int responseTimeInMilli;

    private boolean isUp;

    private String errorMessage;

    @Column(nullable = false)
    private LocalDateTime checkedAt;
}

package com.heartbeat.ping.modles;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

/** Pre-aggregated per-day stats for a monitor, retained after raw logs are purged. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "monitor_daily_stats", uniqueConstraints = {
        @UniqueConstraint(name = "uk_daily_stats_monitor_day", columnNames = {"monitor_id", "stat_day"})
}, indexes = {
        @Index(name = "idx_daily_stats_monitor", columnList = "monitor_id, stat_day")
})
public class MonitorDailyStats {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "monitor_id", nullable = false)
    private UUID monitorId;

    @Column(name = "stat_day", nullable = false)
    private LocalDate day;

    @Column(nullable = false)
    private int totalChecks;

    @Column(nullable = false)
    private int totalUp;

    @Column(nullable = false)
    private int totalDown;

    @Column(nullable = false)
    private double avgResponseMs;

    @Column(nullable = false)
    private long downtimeSeconds;
}

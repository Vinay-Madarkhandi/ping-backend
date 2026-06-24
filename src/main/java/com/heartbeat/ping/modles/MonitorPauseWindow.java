package com.heartbeat.ping.modles;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * A span during which a monitor was administratively paused. Open while {@code resumedAt} is null.
 * {@code openForMonitor} (UNIQUE) guarantees a single open pause window per monitor.
 * Used to exclude paused time from uptime denominators.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "monitor_pause_window", indexes = {
        @Index(name = "idx_pause_window_monitor", columnList = "monitor_id, paused_at")
})
public class MonitorPauseWindow {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "monitor_id", nullable = false)
    private Monitor monitor;

    @Column(nullable = false)
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    private Instant pausedAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    private Instant resumedAt;

    @Column(name = "open_for_monitor", unique = true)
    private UUID openForMonitor;
}

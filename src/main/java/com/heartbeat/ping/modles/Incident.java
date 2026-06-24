package com.heartbeat.ping.modles;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * A confirmed outage period for a monitor. Opened when the alert engine declares DOWN and
 * resolved on recovery. {@code openForMonitor} equals {@code monitor_id} while OPEN and is
 * NULL once resolved, so a UNIQUE constraint on it guarantees at most one open incident per monitor.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "incident", indexes = {
        @Index(name = "idx_incident_monitor_started", columnList = "monitor_id, started_at")
})
public class Incident extends BaseModel {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "monitor_id", nullable = false)
    private Monitor monitor;

    @Column(nullable = false)
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    private Instant startedAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    private Instant resolvedAt;

    private Long durationSeconds;

    @Column(columnDefinition = "text")
    private String failureReason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private IncidentStatus status;

    @Column(name = "open_for_monitor", unique = true)
    private UUID openForMonitor;
}

package com.heartbeat.ping.modles;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * A pre-scheduled span during which a monitor should be automatically paused — no checks, no
 * alerts, excluded from uptime — without the user manually pausing/resuming at the exact times.
 * {@link com.heartbeat.ping.scheduler.MaintenanceWindowWorker} applies it at {@code startsAt} and
 * reverts it at {@code endsAt}.
 *
 * <p>{@code pausedByWindow} exists because a monitor can already be paused for an unrelated reason
 * (manually, or by another overlapping window) when this window's start time arrives — in that case
 * this window must not "own" resuming it later, or it would undo someone else's pause.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "maintenance_window")
public class MaintenanceWindow {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "monitor_id", nullable = false)
    private Monitor monitor;

    private String title;

    @Column(nullable = false)
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    private Instant startsAt;

    @Column(nullable = false)
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    private Instant endsAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    private Instant appliedAt;

    @Builder.Default
    private boolean pausedByWindow = false;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    private Instant revertedAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    private Instant createdAt;
}

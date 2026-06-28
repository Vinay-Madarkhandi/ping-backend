package com.heartbeat.ping.modles;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Per-user daily alert count (one row per user per day). Incremented when a DOWN alert is enqueued
 * and read to enforce the plan's daily alert cap.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_alert_usage")
public class UserAlertUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Calendar day this row counts (UTC). */
    @Column(name = "usage_day", nullable = false)
    private LocalDate day;

    @Column(name = "alerts_sent", nullable = false)
    private int alertsSent;
}

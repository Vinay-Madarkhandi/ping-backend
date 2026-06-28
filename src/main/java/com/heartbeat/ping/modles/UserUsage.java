package com.heartbeat.ping.modles;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Per-user monthly check consumption (one row per user per calendar month). Incremented atomically
 * as each check is recorded; read by the quota enforcement job and the admin usage view.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_usage")
public class UserUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** First day of the calendar month this row counts (UTC). */
    @Column(name = "period_month", nullable = false)
    private LocalDate periodMonth;

    @Column(name = "checks_consumed", nullable = false)
    private long checksConsumed;
}

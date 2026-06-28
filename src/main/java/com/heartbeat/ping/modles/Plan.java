package com.heartbeat.ping.modles;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * A subscription tier and the resource limits that come with it. Reference data seeded by Flyway
 * (FREE, PRO) and editable via SQL — see {@code com.heartbeat.ping.service.PlanService} for the
 * in-memory cache used on hot paths.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "plan")
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    /** Maximum number of (non-archived) monitors the user may own. */
    private int maxMonitors;

    /** Smallest allowed check interval, in milliseconds (the most cost-sensitive limit). */
    private int minIntervalMs;

    /** Largest allowed per-check timeout, in milliseconds. */
    private int maxTimeoutMs;

    /** Maximum number of checks the user may consume per calendar month. */
    private long monthlyCheckQuota;

    /** Days of raw log history retained before purge. */
    private int retentionDays;

    /** Minimum gap between repeated DOWN alerts for the same monitor, in seconds. */
    private int alertCooldownSeconds;

    /** Maximum DOWN alerts delivered to the user per day. */
    private int maxAlertsPerDay;

    // ---- Billing metadata (Razorpay) ----

    /** Price of one billing period, in the smallest currency unit (e.g. paise). 0 for FREE. */
    private long priceAmount;

    /** ISO currency code, e.g. {@code INR}. */
    @Column(nullable = false, length = 3)
    private String currency;

    /** Length of one paid billing period in days. 0 for FREE. */
    private int durationDays;
}

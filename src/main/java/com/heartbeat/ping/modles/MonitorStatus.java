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

    // ---- Alert-engine state machine ----

    @Enumerated(EnumType.STRING)
    @Column(name = "current_state", nullable = false, length = 16)
    @Builder.Default
    private MonitorState currentState = MonitorState.UNKNOWN;

    @Column(name = "consecutive_failures", nullable = false)
    @Builder.Default
    private int consecutiveFailures = 0;

    /** Consecutive successes while DOWN, used for the configurable recovery threshold. */
    @Column(name = "consecutive_successes", nullable = false)
    @Builder.Default
    private int consecutiveSuccesses = 0;

    /** Set when the monitor transitions to DOWN; cleared on recovery. */
    private LocalDateTime downSince;

    /** Last time any alert was dispatched, used to enforce the cooldown window. */
    private LocalDateTime lastAlertSentAt;

    /**
     * Expiry of the TLS certificate presented on the most recent HTTPS check, captured from the
     * check's own TLS session (no separate probe). Null for HTTP monitors or before the first
     * successful TLS handshake.
     */
    private LocalDateTime sslCertExpiresAt;
}

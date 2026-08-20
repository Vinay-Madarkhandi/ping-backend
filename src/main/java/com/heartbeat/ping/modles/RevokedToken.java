package com.heartbeat.ping.modles;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Tracks revoked JWT tokens to implement real logout. The JWT itself remains valid until expiry,
 * but the filter checks this table to deny access for logged-out sessions.
 *
 * <p>Design trade-off: storing every revoked token until its natural expiry grows the table, but
 * avoids the complexity of refresh tokens or Redis. For a small-scale SaaS, this is acceptable.
 * A scheduled cleanup job removes expired entries to prevent unbounded growth.
 */
@Entity
@Table(
    name = "revoked_token",
    indexes = {
        @Index(name = "idx_revoked_token_jti", columnList = "jti"),
        @Index(name = "idx_revoked_token_expires", columnList = "expires_at")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevokedToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * JWT ID (jti claim). This is the unique identifier for the token being revoked.
     */
    @Column(name = "jti", nullable = false, unique = true)
    private String jti;

    /**
     * Email of the user who logged out (for audit trail only, not used for validation).
     */
    @Column(name = "user_email", nullable = false)
    private String userEmail;

    /**
     * When the JWT naturally expires. We can delete this row after this time since the token
     * would be invalid anyway.
     */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /**
     * When the logout happened (audit trail).
     */
    @Column(name = "revoked_at", nullable = false, updatable = false)
    private Instant revokedAt;

    @PrePersist
    protected void onCreate() {
        if (revokedAt == null) {
            revokedAt = Instant.now();
        }
    }
}

package com.heartbeat.ping.modles;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * A durable, self-contained outbound email. Recipient/subject/body are denormalized so the send
 * worker needs no joins. {@code dedupeKey} (UNIQUE) makes enqueue idempotent; {@code nextAttemptAt}
 * doubles as a send-lease so a crash mid-send simply retries after the lease expires.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "email_outbox", indexes = {
        @Index(name = "idx_email_outbox_sendable", columnList = "status, next_attempt_at")
})
public class EmailOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID monitorId;

    @Column(nullable = false, length = 320)
    private String recipient;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EmailStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private int maxAttempts;

    @Column(name = "next_attempt_at", nullable = false)
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    private Instant nextAttemptAt;

    @Column(columnDefinition = "text")
    private String lastError;

    @Column(name = "dedupe_key", nullable = false, unique = true)
    private String dedupeKey;

    @Column(nullable = false)
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    private Instant createdAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    private Instant sentAt;
}

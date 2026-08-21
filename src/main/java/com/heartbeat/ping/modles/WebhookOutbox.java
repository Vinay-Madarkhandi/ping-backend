package com.heartbeat.ping.modles;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * A durable, self-contained outbound webhook delivery, mirroring {@link EmailOutbox}'s shape and
 * guarantees. {@code targetUrl}/{@code channelType} are denormalized so the send worker needs no
 * joins; {@code dedupeKey} (UNIQUE) makes enqueue idempotent per (incident, type, channel).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "webhook_outbox", indexes = {
        @Index(name = "idx_webhook_outbox_sendable", columnList = "status, next_attempt_at")
})
public class WebhookOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID monitorId;

    private UUID channelId;

    @Column(name = "target_url", nullable = false, length = 2048)
    private String targetUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel_type", nullable = false, length = 16)
    private AlertChannelType channelType;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private WebhookStatus status;

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

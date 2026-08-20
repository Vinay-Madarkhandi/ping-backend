package com.heartbeat.ping.modles;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;


@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "users")
public class User extends BaseModel {
    @Column(nullable = false)
    private String userName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private boolean emailVerified = false;

    @Column(nullable = false)
    private String passwordHash;

    /** Subscription plan this user belongs to; determines all usage limits. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    /** Subscription lifecycle for paid plans; FREE for users without an active paid subscription. */
    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_status", nullable = false, length = 16)
    @Builder.Default
    private SubscriptionStatus subscriptionStatus = SubscriptionStatus.FREE;

    @Column(name = "subscription_start_at")
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    private Instant subscriptionStartAt;

    @Column(name = "subscription_end_at")
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    private Instant subscriptionEndAt;

    /** Optional external billing customer id (reserved for future Razorpay customer mapping). */
    @Column(name = "billing_customer_id")
    private String billingCustomerId;

    /**
     * Soft delete timestamp. When set, the user is considered deleted and cannot log in.
     * Email is anonymized to prevent conflicts with new registrations.
     */
    @Column(name = "deleted_at")
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    private Instant deletedAt;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private  List<Monitor> monitors;
}

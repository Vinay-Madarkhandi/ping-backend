package com.heartbeat.ping.modles;

import jakarta.persistence.*;
import lombok.*;

/**
 * One row per payment attempt — the durable audit trail for billing. Created (status CREATED) before
 * checkout, then driven to SUCCESS/FAILED/REFUNDED by signature verification and webhooks.
 * {@code razorpayOrderId} is unique, which (with a pessimistic lock on fulfilment) makes the upgrade
 * idempotent across the verify endpoint and the webhook.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "payment_transactions",
        uniqueConstraints = @UniqueConstraint(name = "uk_payment_txn_order", columnNames = "razorpay_order_id"))
public class PaymentTransaction extends BaseModel {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** The plan the user is paying to move to. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    /** Charged amount in the smallest currency unit (paise). Always resolved from the plan, server-side. */
    @Column(nullable = false)
    private long amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "razorpay_order_id", nullable = false)
    private String razorpayOrderId;

    @Column(name = "razorpay_payment_id")
    private String razorpayPaymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 16)
    private PaymentStatus paymentStatus;

    /** The Razorpay signature captured at verification time (audit). */
    @Column(length = 512)
    private String signature;
}

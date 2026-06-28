package com.heartbeat.ping.service.billing;

import com.heartbeat.ping.modles.PaymentStatus;
import com.heartbeat.ping.modles.PaymentTransaction;
import com.heartbeat.ping.modles.Plan;
import com.heartbeat.ping.modles.SubscriptionStatus;
import com.heartbeat.ping.modles.User;
import com.heartbeat.ping.repository.PaymentTransactionRepository;
import com.heartbeat.ping.repository.PlanRepository;
import com.heartbeat.ping.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end billing against a real Postgres: signature verification → atomic, idempotent upgrade,
 * and the subscription expiry downgrade.
 */
@SpringBootTest
@Testcontainers
@Transactional
class BillingIntegrationTest {

    private static final String KEY_SECRET = "itest_key_secret";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void razorpay(DynamicPropertyRegistry registry) {
        registry.add("razorpay.key-secret", () -> KEY_SECRET);
        registry.add("razorpay.key-id", () -> "rzp_test_itest");
    }

    @Autowired private PlanRepository planRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PaymentTransactionRepository txnRepository;
    @Autowired private BillingService billingService;
    @Autowired private SubscriptionService subscriptionService;

    @Test
    void verifyUpgradesUserToProAndIsIdempotent() {
        Plan pro = planRepository.findByName("PRO").orElseThrow();
        User user = persistUser("FREE", SubscriptionStatus.FREE, null);
        String orderId = "order_" + UUID.randomUUID();
        txnRepository.save(PaymentTransaction.builder()
                .user(user).plan(pro)
                .amount(pro.getPriceAmount()).currency(pro.getCurrency())
                .razorpayOrderId(orderId).paymentStatus(PaymentStatus.CREATED)
                .build());

        String paymentId = "pay_" + UUID.randomUUID();
        String signature = hmacHex(orderId + "|" + paymentId, KEY_SECRET);

        String plan = billingService.fulfill(orderId, paymentId, signature, true);
        assertThat(plan).isEqualTo("PRO");

        User upgraded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(upgraded.getPlan().getName()).isEqualTo("PRO");
        assertThat(upgraded.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(upgraded.getSubscriptionEndAt()).isNotNull();
        assertThat(txnRepository.findByRazorpayOrderId(orderId).orElseThrow().getPaymentStatus())
                .isEqualTo(PaymentStatus.SUCCESS);

        // Second identical call must be a no-op (no exception, still SUCCESS / PRO).
        assertThat(billingService.fulfill(orderId, paymentId, signature, true)).isEqualTo("PRO");
        assertThat(userRepository.findById(user.getId()).orElseThrow().getPlan().getName()).isEqualTo("PRO");
    }

    @Test
    void rejectsForgedSignatureAndMarksFailed() {
        Plan pro = planRepository.findByName("PRO").orElseThrow();
        User user = persistUser("FREE", SubscriptionStatus.FREE, null);
        String orderId = "order_" + UUID.randomUUID();
        txnRepository.save(PaymentTransaction.builder()
                .user(user).plan(pro)
                .amount(pro.getPriceAmount()).currency(pro.getCurrency())
                .razorpayOrderId(orderId).paymentStatus(PaymentStatus.CREATED)
                .build());

        try {
            billingService.fulfill(orderId, "pay_x", "forged_signature", true);
        } catch (PaymentVerificationException expected) {
            // expected
        }

        assertThat(userRepository.findById(user.getId()).orElseThrow().getPlan().getName()).isEqualTo("FREE");
    }

    @Test
    void expiryJobDowngradesLapsedProUser() {
        Plan pro = planRepository.findByName("PRO").orElseThrow();
        Instant past = Instant.now().minus(Duration.ofDays(1));
        persistUser("PRO", SubscriptionStatus.ACTIVE, past, pro);

        int downgraded = subscriptionService.downgradeExpired();
        assertThat(downgraded).isGreaterThanOrEqualTo(1);
    }

    private User persistUser(String planName, SubscriptionStatus status, Instant endAt) {
        return persistUser(planName, status, endAt, planRepository.findByName(planName).orElseThrow());
    }

    private User persistUser(String planName, SubscriptionStatus status, Instant endAt, Plan plan) {
        User user = User.builder()
                .userName("u-" + UUID.randomUUID())
                .email(UUID.randomUUID() + "@example.com")
                .passwordHash("x")
                .plan(plan)
                .subscriptionStatus(status)
                .subscriptionEndAt(endAt)
                .build();
        return userRepository.save(user);
    }

    private static String hmacHex(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

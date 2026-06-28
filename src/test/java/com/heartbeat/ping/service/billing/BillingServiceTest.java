package com.heartbeat.ping.service.billing;

import com.heartbeat.ping.config.properties.RazorpayProperties;
import com.heartbeat.ping.modles.PaymentStatus;
import com.heartbeat.ping.modles.PaymentTransaction;
import com.heartbeat.ping.modles.Plan;
import com.heartbeat.ping.modles.SubscriptionStatus;
import com.heartbeat.ping.modles.User;
import com.heartbeat.ping.repository.MonitorRepository;
import com.heartbeat.ping.repository.PaymentTransactionRepository;
import com.heartbeat.ping.repository.UserRepository;
import com.heartbeat.ping.service.PlanService;
import com.heartbeat.ping.service.billing.dto.CreateOrderResponse;
import com.heartbeat.ping.service.time.DatabaseClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PlanService planService;
    @Mock private RazorpayService razorpayService;
    @Mock private PaymentVerificationService verificationService;
    @Mock private PaymentTransactionRepository txnRepository;
    @Mock private MonitorRepository monitorRepository;
    @Mock private DatabaseClock clock;

    private BillingService service;

    private Plan free;
    private Plan pro;
    private User user;

    @BeforeEach
    void setUp() {
        RazorpayProperties props = new RazorpayProperties();
        props.setKeyId("rzp_test_key");
        service = new BillingService(userRepository, planService, razorpayService, verificationService,
                txnRepository, monitorRepository, props, clock);

        free = Plan.builder().id(UUID.randomUUID()).name("FREE").priceAmount(0).currency("INR").durationDays(0).build();
        pro = Plan.builder().id(UUID.randomUUID()).name("PRO").priceAmount(49900).currency("INR").durationDays(30).build();
        user = User.builder().email("u@example.com").plan(free).build();
        user.setId(UUID.randomUUID());
    }

    // ---- createOrder ----

    @Test
    void createsOrderForPurchasablePlan() {
        when(userRepository.findByEmail("u@example.com")).thenReturn(Optional.of(user));
        when(planService.getByName("PRO")).thenReturn(pro);
        when(razorpayService.createOrder(eq(49900L), eq("INR"), anyString())).thenReturn("order_123");

        CreateOrderResponse res = service.createOrder("u@example.com", "PRO");

        assertThat(res.razorpayOrderId()).isEqualTo("order_123");
        assertThat(res.amount()).isEqualTo(49900L);
        assertThat(res.currency()).isEqualTo("INR");
        assertThat(res.keyId()).isEqualTo("rzp_test_key");
        verify(txnRepository).save(any(PaymentTransaction.class));
    }

    @Test
    void rejectsUnknownPlan() {
        when(userRepository.findByEmail("u@example.com")).thenReturn(Optional.of(user));
        when(planService.getByName("GOLD")).thenReturn(null);

        assertThatThrownBy(() -> service.createOrder("u@example.com", "GOLD"))
                .isInstanceOf(BillingException.class);
        verifyNoInteractions(razorpayService);
    }

    @Test
    void rejectsNonPurchasableFreePlan() {
        when(userRepository.findByEmail("u@example.com")).thenReturn(Optional.of(user));
        when(planService.getByName("FREE")).thenReturn(free);

        assertThatThrownBy(() -> service.createOrder("u@example.com", "FREE"))
                .isInstanceOf(BillingException.class);
    }

    @Test
    void rejectsWhenAlreadyOnTargetPlan() {
        user.setPlan(pro);
        when(userRepository.findByEmail("u@example.com")).thenReturn(Optional.of(user));
        when(planService.getByName("PRO")).thenReturn(pro);

        assertThatThrownBy(() -> service.createOrder("u@example.com", "PRO"))
                .isInstanceOf(BillingException.class);
        verifyNoInteractions(razorpayService);
    }

    // ---- fulfill ----

    @Test
    void fulfillUpgradesUserOnValidSignature() {
        PaymentTransaction txn = txn(PaymentStatus.CREATED);
        when(txnRepository.findByRazorpayOrderIdForUpdate("order_1")).thenReturn(Optional.of(txn));
        when(verificationService.verifyPaymentSignature("order_1", "pay_1", "sig")).thenReturn(true);
        when(clock.now()).thenReturn(Instant.parse("2026-06-15T00:00:00Z"));

        String plan = service.fulfill("order_1", "pay_1", "sig", true);

        assertThat(plan).isEqualTo("PRO");
        assertThat(txn.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(user.getPlan()).isEqualTo(pro);
        assertThat(user.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(user.getSubscriptionEndAt()).isEqualTo(Instant.parse("2026-07-15T00:00:00Z"));
        verify(monitorRepository).clearQuotaBlockForUser(user.getId());
    }

    @Test
    void fulfillIsIdempotentForAlreadySuccessfulPayment() {
        PaymentTransaction txn = txn(PaymentStatus.SUCCESS);
        when(txnRepository.findByRazorpayOrderIdForUpdate("order_1")).thenReturn(Optional.of(txn));

        String plan = service.fulfill("order_1", "pay_1", "sig", true);

        assertThat(plan).isEqualTo("PRO");
        verifyNoInteractions(verificationService);
        verify(monitorRepository, never()).clearQuotaBlockForUser(any());
        assertThat(user.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.FREE); // untouched
    }

    @Test
    void fulfillMarksFailedOnBadSignature() {
        PaymentTransaction txn = txn(PaymentStatus.CREATED);
        when(txnRepository.findByRazorpayOrderIdForUpdate("order_1")).thenReturn(Optional.of(txn));
        when(verificationService.verifyPaymentSignature("order_1", "pay_1", "bad")).thenReturn(false);

        assertThatThrownBy(() -> service.fulfill("order_1", "pay_1", "bad", true))
                .isInstanceOf(PaymentVerificationException.class);
        assertThat(txn.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(monitorRepository, never()).clearQuotaBlockForUser(any());
    }

    private PaymentTransaction txn(PaymentStatus status) {
        return PaymentTransaction.builder()
                .user(user)
                .plan(pro)
                .amount(49900)
                .currency("INR")
                .razorpayOrderId("order_1")
                .paymentStatus(status)
                .build();
    }
}

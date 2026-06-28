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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Billing orchestration: creates Razorpay orders (amount resolved server-side from the plan) and
 * fulfils verified payments into an atomic, idempotent plan upgrade. {@link #fulfill} is shared by
 * the verify endpoint and the webhook; a pessimistic lock on the transaction row plus a SUCCESS
 * re-check guarantees a payment upgrades the user exactly once.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingService {

    private static final String FREE_PLAN = "FREE";

    private final UserRepository userRepository;
    private final PlanService planService;
    private final RazorpayService razorpayService;
    private final PaymentVerificationService verificationService;
    private final PaymentTransactionRepository txnRepository;
    private final MonitorRepository monitorRepository;
    private final RazorpayProperties razorpayProperties;
    private final DatabaseClock clock;

    @Transactional
    public CreateOrderResponse createOrder(String userEmail, String targetPlanName) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new BillingException("User not found"));

        Plan target = planService.getByName(targetPlanName);
        if (target == null) {
            throw new BillingException("Unknown plan: " + targetPlanName);
        }
        if (FREE_PLAN.equalsIgnoreCase(target.getName()) || target.getPriceAmount() <= 0) {
            throw new BillingException("Plan " + target.getName() + " is not purchasable");
        }
        if (user.getPlan() != null && user.getPlan().getId().equals(target.getId())) {
            throw new BillingException("You are already on the " + target.getName() + " plan");
        }

        // Razorpay caps receipt at 40 chars. "rcpt_" + UUID is 41, so use a compact UUID.
        String receipt = "rcpt_" + UUID.randomUUID().toString().replace("-", "");
        String orderId = razorpayService.createOrder(target.getPriceAmount(), target.getCurrency(), receipt);

        txnRepository.save(PaymentTransaction.builder()
                .user(user)
                .plan(target)
                .amount(target.getPriceAmount())
                .currency(target.getCurrency())
                .razorpayOrderId(orderId)
                .paymentStatus(PaymentStatus.CREATED)
                .build());

        return new CreateOrderResponse(orderId, target.getPriceAmount(), target.getCurrency(),
                razorpayProperties.getKeyId());
    }

    /**
     * Fulfils a payment: verifies the signature (when {@code verifySignature}), marks the transaction
     * SUCCESS and upgrades the user — all in one transaction. Idempotent: a transaction already in
     * SUCCESS is returned untouched, so a duplicate verify call or a webhook replay cannot upgrade twice.
     *
     * @param verifySignature true for the verify endpoint (checks the checkout signature); false for
     *                        the webhook path, where the raw-body webhook signature was already verified.
     */
    @Transactional
    public String fulfill(String orderId, String paymentId, String signature, boolean verifySignature) {
        PaymentTransaction txn = txnRepository.findByRazorpayOrderIdForUpdate(orderId)
                .orElseThrow(() -> new BillingException("Unknown order"));

        if (txn.getPaymentStatus() == PaymentStatus.SUCCESS) {
            return txn.getPlan().getName(); // already processed — idempotent no-op
        }

        if (verifySignature && !verificationService.verifyPaymentSignature(orderId, paymentId, signature)) {
            txn.setPaymentStatus(PaymentStatus.FAILED);
            throw new PaymentVerificationException("Payment signature verification failed");
        }

        Instant now = clock.now();
        txn.setRazorpayPaymentId(paymentId);
        txn.setSignature(signature);
        txn.setPaymentStatus(PaymentStatus.SUCCESS);

        User user = txn.getUser();
        Plan plan = txn.getPlan();
        user.setPlan(plan);
        user.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
        user.setSubscriptionStartAt(now);
        user.setSubscriptionEndAt(now.plus(Duration.ofDays(plan.getDurationDays())));

        // PRO quota is higher; clear any quota-block so monitoring resumes immediately.
        monitorRepository.clearQuotaBlockForUser(user.getId());

        log.info("Billing: user {} upgraded to {} (order {})", user.getId(), plan.getName(), orderId);
        return plan.getName();
    }

    @Transactional
    public void markFailed(String orderId) {
        txnRepository.findByRazorpayOrderIdForUpdate(orderId).ifPresent(txn -> {
            if (txn.getPaymentStatus() != PaymentStatus.SUCCESS) {
                txn.setPaymentStatus(PaymentStatus.FAILED);
            }
        });
    }

    @Transactional
    public void markRefunded(String paymentId) {
        txnRepository.findByRazorpayPaymentId(paymentId)
                .ifPresent(txn -> txn.setPaymentStatus(PaymentStatus.REFUNDED));
    }
}

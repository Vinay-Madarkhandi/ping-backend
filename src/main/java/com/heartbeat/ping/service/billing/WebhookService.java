package com.heartbeat.ping.service.billing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

/**
 * Secondary, server-to-server confirmation layer. Verifies the raw-body webhook signature, then
 * reconciles the payment — recovering upgrades when the frontend callback is lost. Trust nothing
 * until the signature checks out.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    private final PaymentVerificationService verificationService;
    private final BillingService billingService;

    public void handle(String rawBody, String signature) {
        if (!verificationService.verifyWebhookSignature(rawBody, signature)) {
            throw new PaymentVerificationException("Webhook signature verification failed");
        }

        JSONObject payload = new JSONObject(rawBody);
        String event = payload.optString("event");
        switch (event) {
            case "payment.captured" -> {
                JSONObject entity = paymentEntity(payload);
                // Same idempotent upgrade path; the webhook body is already verified, so we don't
                // re-check the (absent) checkout signature here.
                billingService.fulfill(entity.getString("order_id"), entity.getString("id"), null, false);
            }
            case "payment.failed" -> billingService.markFailed(paymentEntity(payload).getString("order_id"));
            case "refund.processed" -> billingService.markRefunded(refundEntity(payload).getString("payment_id"));
            default -> log.debug("Ignoring unhandled Razorpay webhook event: {}", event);
        }
    }

    private JSONObject paymentEntity(JSONObject payload) {
        return payload.getJSONObject("payload").getJSONObject("payment").getJSONObject("entity");
    }

    private JSONObject refundEntity(JSONObject payload) {
        return payload.getJSONObject("payload").getJSONObject("refund").getJSONObject("entity");
    }
}

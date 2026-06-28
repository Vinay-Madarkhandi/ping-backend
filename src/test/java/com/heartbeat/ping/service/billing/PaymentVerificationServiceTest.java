package com.heartbeat.ping.service.billing;

import com.heartbeat.ping.config.properties.RazorpayProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentVerificationServiceTest {

    private static final String KEY_SECRET = "key_secret_value";
    private static final String WEBHOOK_SECRET = "webhook_secret_value";

    private PaymentVerificationService service;

    @BeforeEach
    void setUp() {
        RazorpayProperties props = new RazorpayProperties();
        props.setKeySecret(KEY_SECRET);
        props.setWebhookSecret(WEBHOOK_SECRET);
        service = new PaymentVerificationService(props);
    }

    @Test
    void acceptsValidPaymentSignature() {
        String orderId = "order_ABC123";
        String paymentId = "pay_XYZ789";
        String signature = hmacHex(orderId + "|" + paymentId, KEY_SECRET);

        assertThat(service.verifyPaymentSignature(orderId, paymentId, signature)).isTrue();
    }

    @Test
    void rejectsInvalidPaymentSignature() {
        assertThat(service.verifyPaymentSignature("order_ABC123", "pay_XYZ789", "deadbeef")).isFalse();
    }

    @Test
    void rejectsTamperedPayload() {
        String signature = hmacHex("order_ABC123|pay_XYZ789", KEY_SECRET);
        // Same signature, but the order id was tampered with.
        assertThat(service.verifyPaymentSignature("order_TAMPERED", "pay_XYZ789", signature)).isFalse();
    }

    @Test
    void acceptsValidWebhookSignature() {
        String body = "{\"event\":\"payment.captured\"}";
        assertThat(service.verifyWebhookSignature(body, hmacHex(body, WEBHOOK_SECRET))).isTrue();
    }

    @Test
    void rejectsWebhookSignedWithWrongSecret() {
        String body = "{\"event\":\"payment.captured\"}";
        assertThat(service.verifyWebhookSignature(body, hmacHex(body, "wrong_secret"))).isFalse();
    }

    /** Independent HMAC-SHA256 hex, to cross-check the service's own computation. */
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

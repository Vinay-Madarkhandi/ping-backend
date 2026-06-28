package com.heartbeat.ping.service.billing;

import com.heartbeat.ping.config.properties.RazorpayProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Cryptographic verification of Razorpay signatures — no external calls, so it is fully unit-testable
 * and identical in test and live. Comparison is constant-time ({@link MessageDigest#isEqual}) to
 * avoid timing oracles, and the same logic runs regardless of environment (no test-mode skipping).
 */
@Service
@RequiredArgsConstructor
public class PaymentVerificationService {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private final RazorpayProperties props;

    /** Verifies a checkout signature: HMAC_SHA256("{orderId}|{paymentId}", keySecret). */
    public boolean verifyPaymentSignature(String orderId, String paymentId, String signature) {
        return verify(orderId + "|" + paymentId, signature, props.getKeySecret());
    }

    /** Verifies a webhook signature: HMAC_SHA256(rawBody, webhookSecret). Body must be the raw bytes. */
    public boolean verifyWebhookSignature(String rawBody, String signature) {
        return verify(rawBody, signature, props.getWebhookSecret());
    }

    private boolean verify(String payload, String providedSignature, String secret) {
        if (providedSignature == null || secret == null || secret.isBlank()) {
            return false;
        }
        String expected = hmacSha256Hex(payload, secret);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                providedSignature.getBytes(StandardCharsets.UTF_8));
    }

    private String hmacSha256Hex(String data, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(raw.length * 2);
            for (byte b : raw) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC computation failed", e);
        }
    }
}

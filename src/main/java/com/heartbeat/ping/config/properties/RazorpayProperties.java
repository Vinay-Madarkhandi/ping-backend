package com.heartbeat.ping.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Razorpay credentials ({@code razorpay.*}). The same code path serves test and live; only these
 * values differ between environments (set via {@code RAZORPAY_KEY_ID/KEY_SECRET/WEBHOOK_SECRET}).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "razorpay")
public class RazorpayProperties {

    /** Public key id (rzp_test_* or rzp_live_*); also returned to the checkout widget. */
    private String keyId;

    /** Secret used to sign orders and verify payment signatures. */
    private String keySecret;

    /** Separate secret used to verify webhook payload signatures. */
    private String webhookSecret;
}

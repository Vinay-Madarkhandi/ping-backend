package com.heartbeat.ping.service.billing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** The three fields the Razorpay checkout returns; the frontend forwards them verbatim. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class VerifyPaymentRequest {

    @NotBlank
    @JsonProperty("razorpay_payment_id")
    private String razorpayPaymentId;

    @NotBlank
    @JsonProperty("razorpay_order_id")
    private String razorpayOrderId;

    @NotBlank
    @JsonProperty("razorpay_signature")
    private String razorpaySignature;
}

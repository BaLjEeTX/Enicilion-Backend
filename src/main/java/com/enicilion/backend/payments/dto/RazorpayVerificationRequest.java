package com.enicilion.backend.payments.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RazorpayVerificationRequest {

    @NotBlank(message = "Payment ID is required")
    @JsonProperty("payment_id")
    private String paymentId;

    @NotBlank(message = "Razorpay Order ID is required")
    @JsonProperty("razorpay_order_id")
    private String razorpayOrderId;

    @NotBlank(message = "Razorpay Payment ID is required")
    @JsonProperty("razorpay_payment_id")
    private String razorpayPaymentId;

    @NotBlank(message = "Signature is required")
    @JsonProperty("razorpay_signature")
    private String razorpaySignature;
}

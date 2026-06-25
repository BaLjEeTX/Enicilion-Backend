package com.enicilion.backend.tickets.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {

    @JsonProperty("payment_id")
    private String paymentId;

    private BigDecimal amount;

    private String currency;

    @JsonProperty("provider_order_id")
    private String providerOrderId;

    @JsonProperty("key")
    private String key;
}

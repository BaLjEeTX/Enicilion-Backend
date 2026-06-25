package com.enicilion.backend.tickets.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CheckoutRequest {

    @NotEmpty(message = "Cart items cannot be empty")
    @Valid
    private List<CartItem> items;

    private String couponCode;

    private Boolean premiumSpiritsAcknowledged;

    private BigDecimal clientTotal;

    private String idempotencyKey; // Client-provided key for double-charge prevention

    @JsonProperty("guest_name")
    private String guestName;

    @JsonProperty("guest_email")
    private String guestEmail;

    @JsonProperty("guest_phone")
    private String guestPhone;
}

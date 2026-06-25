package com.enicilion.backend.coupons.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ValidateCouponRequest {

    @NotBlank(message = "Coupon code is required")
    private String code;

    @NotNull(message = "Subtotal is required")
    private BigDecimal subtotal;

    private List<Object> items; // Can contain items in request, but not strictly used for simple coupon calculation in this controller
}

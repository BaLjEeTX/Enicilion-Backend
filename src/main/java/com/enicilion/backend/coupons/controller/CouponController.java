package com.enicilion.backend.coupons.controller;

import com.enicilion.backend.common.dto.ApiResponse;
import com.enicilion.backend.coupons.dto.ValidateCouponRequest;
import com.enicilion.backend.coupons.service.CouponService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    @PostMapping("/validate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> validateCoupon(
            @Valid @RequestBody ValidateCouponRequest request) {
        
        try {
            Map<String, Object> result = couponService.validateCoupon(request.getCode(), request.getSubtotal());
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }
}

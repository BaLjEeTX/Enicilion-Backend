package com.enicilion.backend.coupons.service;

import com.enicilion.backend.common.exception.ResourceNotFoundException;
import com.enicilion.backend.coupons.entity.Coupon;
import com.enicilion.backend.coupons.repository.CouponRepository;
import com.enicilion.backend.coupons.repository.CouponUsageRepository;
import com.enicilion.backend.payments.entity.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;
    private final CouponUsageRepository couponUsageRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> validateCoupon(String code, BigDecimal subtotal) {
        String normalizedCode = code != null ? code.trim().toUpperCase() : "";
        Coupon coupon = couponRepository.findByCode(normalizedCode)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon not found"));

        validateCouponState(coupon);

        // Calculate dynamic discount percentage (default 10%)
        BigDecimal discountRate = BigDecimal.valueOf(coupon.getDiscountPercentage()).divide(BigDecimal.valueOf(100.0));
        BigDecimal discountAmount = subtotal.multiply(discountRate)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = subtotal.subtract(discountAmount)
                .setScale(2, RoundingMode.HALF_UP);

        Map<String, Object> data = new HashMap<>();
        data.put("code", coupon.getCode());
        data.put("discountAmount", discountAmount);
        data.put("total", total);
        data.put("discountPercentage", coupon.getDiscountPercentage());

        return data;
    }

    public void validateCouponState(Coupon coupon) {
        if (!coupon.isActive()) {
            throw new IllegalArgumentException("Coupon is inactive");
        }

        // Check validity period
        OffsetDateTime now = OffsetDateTime.now();
        if (coupon.getValidFrom() != null && coupon.getValidFrom().isAfter(now)) {
            throw new IllegalArgumentException("Coupon validity period has not started yet");
        }
        if (coupon.getValidUntil() != null && coupon.getValidUntil().isBefore(now)) {
            throw new IllegalArgumentException("Coupon has expired");
        }

        UUID couponId = coupon.getId();
        long paidUsages = couponUsageRepository.countByCouponIdAndPaymentStatus(couponId, PaymentStatus.paid);
        long activeReservations = couponUsageRepository.countByCouponIdAndPaymentStatusAndUsedAtAfter(
                couponId, PaymentStatus.pending, now.minusMinutes(15));

        long totalReservedAndUsed = paidUsages + activeReservations;

        if (totalReservedAndUsed >= coupon.getMaxUses()) {
            if (paidUsages >= coupon.getMaxUses()) {
                throw new IllegalArgumentException("Coupon already used");
            } else {
                throw new IllegalArgumentException("Coupon is currently reserved by another pending transaction");
            }
        }
    }
}

package com.enicilion.backend.coupons.service;

import com.enicilion.backend.coupons.entity.Coupon;
import com.enicilion.backend.coupons.repository.CouponRepository;
import com.enicilion.backend.coupons.repository.CouponUsageRepository;
import com.enicilion.backend.payments.entity.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CouponServiceTest {

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private CouponUsageRepository couponUsageRepository;

    @InjectMocks
    private CouponService couponService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testValidateCouponState_ActiveAndUnderLimit_Success() {
        UUID couponId = UUID.randomUUID();
        Coupon coupon = Coupon.builder()
                .id(couponId)
                .code("SAVE10")
                .isActive(true)
                .maxUses(5)
                .usedCount(2)
                .build();

        when(couponUsageRepository.countByCouponIdAndPaymentStatus(eq(couponId), eq(PaymentStatus.paid))).thenReturn(2L);
        when(couponUsageRepository.countByCouponIdAndPaymentStatusAndUsedAtAfter(eq(couponId), eq(PaymentStatus.pending), any(OffsetDateTime.class))).thenReturn(1L);

        // total is 3 (2 paid + 1 pending), maxUses is 5. So it should validate successfully.
        assertDoesNotThrow(() -> couponService.validateCouponState(coupon));
    }

    @Test
    void testValidateCouponState_Inactive_ThrowsException() {
        Coupon coupon = Coupon.builder()
                .code("INACTIVE")
                .isActive(false)
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> couponService.validateCouponState(coupon));
        assertEquals("Coupon is inactive", ex.getMessage());
    }

    @Test
    void testValidateCouponState_AlreadyFullyUsed_ThrowsException() {
        UUID couponId = UUID.randomUUID();
        Coupon coupon = Coupon.builder()
                .id(couponId)
                .code("SAVE10")
                .isActive(true)
                .maxUses(5)
                .usedCount(5)
                .build();

        when(couponUsageRepository.countByCouponIdAndPaymentStatus(eq(couponId), eq(PaymentStatus.paid))).thenReturn(5L);
        when(couponUsageRepository.countByCouponIdAndPaymentStatusAndUsedAtAfter(eq(couponId), eq(PaymentStatus.pending), any(OffsetDateTime.class))).thenReturn(0L);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> couponService.validateCouponState(coupon));
        assertEquals("Coupon already used", ex.getMessage());
    }

    @Test
    void testValidateCouponState_FullyReserved_ThrowsException() {
        UUID couponId = UUID.randomUUID();
        Coupon coupon = Coupon.builder()
                .id(couponId)
                .code("SAVE10")
                .isActive(true)
                .maxUses(5)
                .usedCount(3)
                .build();

        when(couponUsageRepository.countByCouponIdAndPaymentStatus(eq(couponId), eq(PaymentStatus.paid))).thenReturn(3L);
        when(couponUsageRepository.countByCouponIdAndPaymentStatusAndUsedAtAfter(eq(couponId), eq(PaymentStatus.pending), any(OffsetDateTime.class))).thenReturn(2L);

        // total 5 (3 paid + 2 pending), maxUses is 5. So fully reserved.
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> couponService.validateCouponState(coupon));
        assertEquals("Coupon is currently reserved by another pending transaction", ex.getMessage());
    }
}

package com.enicilion.backend.coupons.repository;

import com.enicilion.backend.coupons.entity.CouponUsage;
import com.enicilion.backend.payments.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CouponUsageRepository extends JpaRepository<CouponUsage, UUID> {
    
    Optional<CouponUsage> findByPaymentId(UUID paymentId);

    @Query("SELECT COUNT(cu) FROM CouponUsage cu WHERE cu.coupon.id = :couponId AND cu.payment.status = :status")
    long countByCouponIdAndPaymentStatus(UUID couponId, PaymentStatus status);

    @Query("SELECT COUNT(cu) FROM CouponUsage cu WHERE cu.coupon.id = :couponId AND cu.payment.status = :status AND cu.usedAt > :since")
    long countByCouponIdAndPaymentStatusAndUsedAtAfter(UUID couponId, PaymentStatus status, OffsetDateTime since);
}

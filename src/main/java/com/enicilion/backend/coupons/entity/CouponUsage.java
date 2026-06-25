package com.enicilion.backend.coupons.entity;

import com.enicilion.backend.common.base.BaseEntity;
import com.enicilion.backend.payments.entity.Payment;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "coupon_usages", indexes = {
    @Index(name = "idx_coupon_usages_coupon_id", columnList = "coupon_id"),
    @Index(name = "idx_coupon_usages_payment_id", columnList = "payment_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"coupon", "payment"})
public class CouponUsage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "UUID")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_id", nullable = false)
    private Coupon coupon;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(name = "used_at", nullable = false)
    private OffsetDateTime usedAt;
}

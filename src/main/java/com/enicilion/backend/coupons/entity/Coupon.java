package com.enicilion.backend.coupons.entity;

import com.enicilion.backend.common.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "coupons", indexes = {
    @Index(name = "idx_coupons_is_active", columnList = "is_active")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true, exclude = "usages")
public class Coupon extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "UUID")
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String code;

    @Column(name = "max_uses", nullable = false)
    @Builder.Default
    private int maxUses = 1;

    @Column(name = "used_count", nullable = false)
    @Builder.Default
    private int usedCount = 0;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @OneToMany(mappedBy = "coupon", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CouponUsage> usages = new ArrayList<>();


    @Column(name = "discount_percentage", nullable = false)
    @Builder.Default
    private int discountPercentage = 10;

    @Column(name = "valid_from")
    private OffsetDateTime validFrom;

    @Column(name = "valid_until")
    private OffsetDateTime validUntil;

    @Column(name = "is_influencer_coupon", nullable = false)
    @Builder.Default
    private boolean isInfluencerCoupon = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "influencer_profile_id")
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private com.enicilion.backend.influencer.entity.InfluencerProfile influencerProfile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applicable_event_id")
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private com.enicilion.backend.tickets.entity.Event applicableEvent;
}

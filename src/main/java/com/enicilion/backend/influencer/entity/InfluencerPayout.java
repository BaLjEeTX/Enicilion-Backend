package com.enicilion.backend.influencer.entity;

import com.enicilion.backend.common.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "influencer_payouts", indexes = {
    @Index(name = "idx_influencer_payouts_profile_id", columnList = "influencer_profile_id"),
    @Index(name = "idx_influencer_payouts_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class InfluencerPayout extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "UUID")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "influencer_profile_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private InfluencerProfile influencerProfile;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private PayoutStatus status = PayoutStatus.pending;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;
}

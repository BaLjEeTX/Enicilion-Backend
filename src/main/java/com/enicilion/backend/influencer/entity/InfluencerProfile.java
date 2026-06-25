package com.enicilion.backend.influencer.entity;

import com.enicilion.backend.auth.entity.User;
import com.enicilion.backend.common.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "influencer_profiles", indexes = {
    @Index(name = "idx_influencer_profiles_user_id", columnList = "user_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class InfluencerProfile extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "UUID")
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "commission_type", nullable = false, length = 50)
    @Builder.Default
    private CommissionType commissionType = CommissionType.percentage;

    @Column(name = "commission_value", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal commissionValue = BigDecimal.valueOf(10.00);
}

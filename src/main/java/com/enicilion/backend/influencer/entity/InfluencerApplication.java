package com.enicilion.backend.influencer.entity;

import com.enicilion.backend.auth.entity.User;
import com.enicilion.backend.common.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "influencer_applications", indexes = {
    @Index(name = "idx_influencer_applications_user_id", columnList = "user_id"),
    @Index(name = "idx_influencer_applications_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class InfluencerApplication extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "UUID")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private User user;

    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(nullable = false, length = 30)
    private String phone;

    @Column(name = "social_links", nullable = false, columnDefinition = "TEXT")
    private String socialLinks;

    @Column(name = "follower_count", nullable = false)
    private int followerCount;

    @Column(name = "niche_description", nullable = false, columnDefinition = "TEXT")
    private String nicheDescription;

    @Column(name = "payment_details", nullable = false, columnDefinition = "TEXT")
    private String paymentDetails;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private ApplicationStatus status = ApplicationStatus.PENDING;

    @Column(columnDefinition = "TEXT")
    private String notes;
}

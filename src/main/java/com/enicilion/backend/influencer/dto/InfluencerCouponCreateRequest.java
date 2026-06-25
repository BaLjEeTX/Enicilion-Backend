package com.enicilion.backend.influencer.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class InfluencerCouponCreateRequest {

    @NotBlank(message = "Coupon code is required")
    private String code;

    @NotNull(message = "Discount percentage is required")
    @Min(value = 0, message = "Discount cannot be negative")
    @Max(value = 100, message = "Discount cannot exceed 100%")
    private Integer discountPercentage;

    private OffsetDateTime validFrom;

    private OffsetDateTime validUntil;

    private Integer maxUses;

    private UUID applicableEventId;

    @NotNull(message = "Influencer profile ID is required")
    private UUID influencerProfileId;
}

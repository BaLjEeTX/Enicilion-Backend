package com.enicilion.backend.influencer.dto;

import com.enicilion.backend.influencer.entity.ApplicationStatus;
import com.enicilion.backend.influencer.entity.CommissionType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class InfluencerApplicationActionRequest {

    @NotNull(message = "Status is required")
    private ApplicationStatus status;

    private String notes;

    // Below fields are used only when status is APPROVED to configure the creator profile
    private CommissionType commissionType;

    private BigDecimal commissionValue;

    private String couponCode;

    private Integer discountPercentage;
}

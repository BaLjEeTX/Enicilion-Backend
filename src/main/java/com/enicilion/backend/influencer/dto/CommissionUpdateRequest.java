package com.enicilion.backend.influencer.dto;

import com.enicilion.backend.influencer.entity.CommissionType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CommissionUpdateRequest {

    @NotNull(message = "Commission type is required")
    private CommissionType commissionType;

    @NotNull(message = "Commission value is required")
    private BigDecimal commissionValue;
}

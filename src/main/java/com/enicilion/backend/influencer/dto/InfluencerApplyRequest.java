package com.enicilion.backend.influencer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class InfluencerApplyRequest {

    @NotBlank(message = "Full name is required")
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Phone number is required")
    private String phone;

    @NotBlank(message = "Social media links are required")
    private String socialLinks;

    @NotNull(message = "Follower count is required")
    @Min(value = 0, message = "Follower count cannot be negative")
    private Integer followerCount;

    @NotBlank(message = "Audience/Niche description is required")
    private String nicheDescription;

    @NotBlank(message = "Payment details are required")
    private String paymentDetails;
}

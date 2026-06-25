package com.enicilion.backend.tickets.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.UUID;

@Data
public class BoxOfficeRequest {
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Full name is required")
    @JsonProperty("full_name")
    private String fullName;

    private String phone;

    @JsonProperty("tier_id")
    private UUID tierId;

    private Integer quantity;

    private java.util.List<Item> items;

    @Data
    public static class Item {
        @NotNull(message = "Tier ID is required")
        @JsonProperty("tier_id")
        private UUID tierId;

        @Min(value = 1, message = "Quantity must be at least 1")
        private int quantity;
    }
}

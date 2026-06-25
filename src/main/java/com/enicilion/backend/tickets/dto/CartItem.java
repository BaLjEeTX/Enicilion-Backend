package com.enicilion.backend.tickets.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CartItem {

    @NotNull(message = "Event ID is required")
    private UUID eventId;

    @NotNull(message = "Tier ID is required")
    private UUID tierId;

    @Min(value = 1, message = "Quantity must be at least 1")
    private int quantity;
}

package com.enicilion.backend.support.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SupportTicketRequest {

    @NotBlank(message = "Category is required")
    private String category;

    @NotBlank(message = "Message is required")
    private String message;

    private String name;
    private String phone;
    private String email;
}

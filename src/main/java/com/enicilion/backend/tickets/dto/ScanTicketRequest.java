package com.enicilion.backend.tickets.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ScanTicketRequest {

    @NotBlank(message = "Ticket code is required")
    private String code;

    private String password;
}

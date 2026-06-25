package com.enicilion.backend.tickets.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScanResponse {
    private boolean valid;
    private String message;
    private String name;
    private String tierName;

    public ScanResponse(boolean valid, String message, String name) {
        this.valid = valid;
        this.message = message;
        this.name = name;
        this.tierName = null;
    }
}

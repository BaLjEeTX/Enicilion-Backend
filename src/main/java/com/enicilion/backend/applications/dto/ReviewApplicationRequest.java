package com.enicilion.backend.applications.dto;

import com.enicilion.backend.applications.entity.ApplicationStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReviewApplicationRequest {

    @NotNull(message = "Review status is required")
    private ApplicationStatus status;

    private String adminNotes;
}

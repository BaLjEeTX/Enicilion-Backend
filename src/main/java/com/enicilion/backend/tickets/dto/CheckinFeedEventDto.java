package com.enicilion.backend.tickets.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckinFeedEventDto {
    private String ticketCode;
    private String action;
    private String gate;
    private String reason;
    private String buyerName;
    private String buyerEmail;
    private String tierName;
    private OffsetDateTime createdAt;
    private int totalCheckedIn;
}

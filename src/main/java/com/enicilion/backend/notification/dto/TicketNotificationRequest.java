package com.enicilion.backend.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketNotificationRequest {
    private String userName;       // Full name of buyer
    private String userEmail;      // Email address
    private String userPhone;      // WhatsApp number
    private String ticketCode;     // Unique ticket code e.g. "PASS-ABC123"
    private String eventName;      // Event name
    private String eventDate;      // e.g. "Saturday, June 13, 2026"
    private String eventLocation;  // e.g. "Sector 88, Mohali"
    private String orderId;        // Payment/order ID
    private int    quantity;       // Number of tickets in this order
    private String tierName;       // Ticket category e.g. "Spectator Pass"
}

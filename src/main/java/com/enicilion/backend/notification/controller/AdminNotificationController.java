package com.enicilion.backend.notification.controller;

import com.enicilion.backend.common.dto.ApiResponse;
import com.enicilion.backend.common.exception.ResourceNotFoundException;
import com.enicilion.backend.notification.dto.TicketNotificationRequest;
import com.enicilion.backend.notification.service.NotificationService;
import com.enicilion.backend.tickets.entity.SpectatorTicket;
import com.enicilion.backend.tickets.repository.TicketRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin/notifications")
@RequiredArgsConstructor
public class AdminNotificationController {

    private final NotificationService notificationService;
    private final TicketRepository ticketRepository;

    /**
     * POST /api/admin/notifications/send-ticket
     *
     * Admin manually sends ticket (WhatsApp + Email) to a user.
     *
     * Request body:
     * {
     *   "ticketCode": "PASS-ABC123",      // required — looks up ticket from DB
     *   "overrideEmail": "x@y.com",       // optional — override email
     *   "overridePhone": "9876543210"     // optional — override phone
     * }
     */
    @PostMapping("/send-ticket")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sendTicket(@RequestBody SendTicketRequest body) {

        if (body.getTicketCode() == null || body.getTicketCode().isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("ticketCode is required"));
        }

        SpectatorTicket ticket = ticketRepository.findByTicketCodeWithDetails(body.getTicketCode())
            .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + body.getTicketCode()));

        String email = body.getOverrideEmail() != null && !body.getOverrideEmail().isBlank()
            ? body.getOverrideEmail()
            : ticket.getUser().getEmail();

        String phone = body.getOverridePhone() != null && !body.getOverridePhone().isBlank()
            ? body.getOverridePhone()
            : ticket.getUser().getWhatsapp();

        notificationService.queueTicketConfirmation(body.getTicketCode(), body.getOverrideEmail(), body.getOverridePhone());

        log.info("[Admin] Manual sendTicket queued for ticketCode={} by admin", body.getTicketCode());
        return ResponseEntity.ok(ApiResponse.success(Map.of(
            "message", "Ticket notification queued for " + email + " and " + phone
        )));
    }

    @Data
    public static class SendTicketRequest {
        private String ticketCode;
        private String overrideEmail;
        private String overridePhone;
    }
}

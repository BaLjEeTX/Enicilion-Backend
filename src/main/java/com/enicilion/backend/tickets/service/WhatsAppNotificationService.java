package com.enicilion.backend.tickets.service;

import com.enicilion.backend.tickets.entity.SpectatorTicket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * AiSensy WhatsApp notification service.
 * Sends a template-based WhatsApp message with ticket details and a PDF download link.
 * Spec §5.3: triggered after successful Razorpay payment verification.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppNotificationService {

    @Value("${app.aisensy.api-key}")
    private String apiKey;

    @Value("${app.aisensy.campaign-name}")
    private String campaignName;

    private final RestTemplate restTemplate;

    private static final String AISENSY_API_URL =
            "https://backend.aisensy.com/dev/api/v2/channels/templates/send";

    /**
     * Send a WhatsApp confirmation message for all tickets in a payment.
     * Runs asynchronously so it does not block the HTTP response.
     */
    @Async
    public void sendTicketConfirmationWhatsApp(List<SpectatorTicket> tickets) {
        if (tickets == null || tickets.isEmpty()) return;

        SpectatorTicket first = tickets.get(0);
        String whatsapp = first.getUser().getWhatsapp();

        if (whatsapp == null || whatsapp.isBlank()) {
            log.debug("No WhatsApp number for user {}, skipping WA notification", first.getUser().getId());
            return;
        }

        try {
            String customerName    = first.getUser().getFullName();
            String tierName        = first.getTier() != null ? first.getTier().getName() : "General Admission";
            String firstTicketCode = first.getTicketCode();

            // Build comma-separated list of all codes for this payment
            String allCodes = tickets.stream()
                    .map(SpectatorTicket::getTicketCode)
                    .collect(Collectors.joining(", "));

            String pdfLink = "https://enicilion.com/api/ticket-pdf/" + firstTicketCode;

            // Template params match AiSensy template variable positions defined in §5.3
            List<String> templateParams = List.of(customerName, tierName, allCodes, pdfLink);

            // Normalise WhatsApp number to E.164 format (add 91 prefix for Indian numbers)
            String destination = normalisePhone(whatsapp);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("apiKey",        apiKey);
            payload.put("campaignName",  campaignName);
            payload.put("destination",   destination);
            payload.put("templateParams", templateParams);
            payload.put("source",        "api-gateway");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    AISENSY_API_URL, HttpMethod.POST, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("WhatsApp confirmation sent to {} for ticket {}", destination, firstTicketCode);
            } else {
                log.warn("AiSensy returned {} for destination {}", response.getStatusCode(), destination);
            }

        } catch (Exception e) {
            // Non-fatal – ticket is already paid, WA is a bonus notification
            log.error("Failed to send WhatsApp notification: {}", e.getMessage(), e);
        }
    }

    /**
     * Normalise an Indian mobile number to international E.164 format.
     * Strips spaces/dashes and prepends '91' if not already present.
     */
    private String normalisePhone(String raw) {
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.startsWith("91") && digits.length() == 12) return digits;
        if (digits.length() == 10) return "91" + digits;
        return digits; // Return as-is for non-standard formats
    }
}

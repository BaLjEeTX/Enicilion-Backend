package com.enicilion.backend.notification.service;

import com.enicilion.backend.config.NotificationProperties;
import com.enicilion.backend.notification.dto.TicketNotificationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppService {

    private final NotificationProperties props;
    private final RestTemplate restTemplate;

    /**
     * Sends ticket confirmation WhatsApp to the user.
     * Uses Meta Cloud API + pre-approved template enc_ticket_confirmed_v2.
     * PDF is sent as a document in the message header.
     */
    public void sendTicketConfirmed(TicketNotificationRequest req) {
        String phone = normalizePhone(req.getUserPhone());
        if (phone == null) {
            log.warn("[WhatsApp] Skipping — invalid/missing phone for ticketCode={}", req.getTicketCode());
            return;
        }

        String pdfUrl = props.getPdf().getBaseUrl()
            + "/ticket-pdf/" + req.getTicketCode()
            + "?v=" + System.currentTimeMillis();

        String templateName = props.getWhatsapp().getTemplateName();
        List<Map<String, String>> bodyParameters;

        if (templateName != null && (templateName.endsWith("_v1") || templateName.endsWith("_v2"))) {
            // Legacy/fallback parameters (3 params)
            bodyParameters = List.of(
                Map.of("type", "text", "text", safe(req.getUserName())),
                Map.of("type", "text", "text", safe(req.getTierName(), "General Admission")),
                Map.of("type", "text", "text", safe(req.getOrderId()))
            );
        } else {
            // Default modern parameters (6 params) for v3, v4, and any custom templates
            bodyParameters = List.of(
                Map.of("type", "text", "text", safe(req.getUserName())),
                Map.of("type", "text", "text", safe(req.getEventName())),
                Map.of("type", "text", "text", safe(req.getTierName(), "General Admission")),
                Map.of("type", "text", "text", safe(req.getOrderId())),
                Map.of("type", "text", "text", safe(req.getEventDate())),
                Map.of("type", "text", "text", safe(req.getEventLocation()))
            );
        }

        // Build request body
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messaging_product", "whatsapp");
        body.put("to", phone);
        body.put("type", "template");
        body.put("template", Map.of(
            "name", templateName,
            "language", Map.of("code", "en"),
            "components", List.of(
                // Header: PDF document
                Map.of(
                    "type", "header",
                    "parameters", List.of(Map.of(
                        "type", "document",
                        "document", Map.of(
                            "link",     pdfUrl,
                            "filename", "ticket.pdf"
                        )
                    ))
                ),
                // Body: text variables
                Map.of(
                    "type", "body",
                    "parameters", bodyParameters
                )
            )
        ));

        post(body, "sendTicketConfirmed", phone);
    }

    // ── Core HTTP Caller ────────────────────────────────────────────────────

    private void post(Map<String, Object> body, String context, String destination) {
        NotificationProperties.WhatsApp wa = props.getWhatsapp();
        String url = wa.getApiUrl() + "/" + wa.getPhoneNumberId() + "/messages";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(wa.getToken());

        log.info("[WhatsApp] {} → {}", context, destination);
        try {
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            log.info("[WhatsApp] ✅ sent [{}] to {} — status={}", context, destination, response.getStatusCode());
        } catch (Exception ex) {
            // NEVER rethrow — notification failure must NOT affect payment flow
            log.error("[WhatsApp] ❌ failed [{}] to {} — {}", context, destination, ex.getMessage(), ex);
        }
    }

    // ── Phone Normalization ─────────────────────────────────────────────────
    // Meta needs digits only, no + prefix.
    // "+919876543210" → "919876543210"
    // "9876543210"    → "919876543210"  (auto-prepend India country code)

    private String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) return null;
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() < 8) return null;
        if (digits.length() == 10) digits = "91" + digits; // India: adjust if different country
        return digits;
    }

    private String safe(String value) { return safe(value, "-"); }
    private String safe(String value, String fallback) {
        return (value != null && !value.isBlank()) ? value.trim() : fallback;
    }
}

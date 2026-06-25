package com.enicilion.backend.notification.service;

import com.enicilion.backend.config.NotificationProperties;
import com.enicilion.backend.notification.dto.TicketNotificationRequest;
import com.enicilion.backend.tickets.entity.SpectatorTicket;
import com.enicilion.backend.tickets.repository.TicketRepository;
import com.enicilion.backend.tickets.service.PdfService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private static final java.time.format.DateTimeFormatter DATE_FORMATTER = java.time.format.DateTimeFormatter
            .ofPattern("dd MMM yyyy, hh:mm a")
            .withZone(java.time.ZoneId.of("Asia/Kolkata"));

    private final NotificationProperties props;
    private final RestTemplate restTemplate;
    private final TicketRepository ticketRepository;
    private final PdfService pdfService;

    /**
     * Sends ticket confirmation email with PDF attached.
     * Uses ZeptoMail REST API.
     */
    public void sendTicketConfirmationEmail(TicketNotificationRequest req) {
        if (req.getUserEmail() == null || req.getUserEmail().isBlank()) {
            log.warn("[Email] Skipping — no email for ticketCode={}", req.getTicketCode());
            return;
        }

        try {
            // Find all tickets under the same payment to build a consolidated PDF
            SpectatorTicket ticket = ticketRepository.findByTicketCodeWithDetails(req.getTicketCode()).orElse(null);
            List<SpectatorTicket> tickets;
            if (ticket != null) {
                if (ticket.getPayment() != null) {
                    tickets = ticketRepository.findByPaymentId(ticket.getPayment().getId());
                    // Eager initialize lazy fields
                    for (SpectatorTicket t : tickets) {
                        if (t.getUser() != null) {
                            t.getUser().getEmail();
                            t.getUser().getFullName();
                            t.getUser().getWhatsapp();
                        }
                        if (t.getTier() != null) {
                            t.getTier().getName();
                            t.getTier().getPrice();
                        }
                        if (t.getEvent() != null) {
                            t.getEvent().getName();
                            t.getEvent().getEventDate();
                            t.getEvent().getLocation();
                        }
                        if (t.getPayment() != null) {
                            t.getPayment().getId();
                            t.getPayment().getAmount();
                            t.getPayment().getCurrency();
                            t.getPayment().getPaidAt();
                            t.getPayment().getProvider();
                        }
                    }
                } else {
                    tickets = Collections.singletonList(ticket);
                }
            } else {
                log.warn("[Email] Ticket not found in DB for code={}. Using fallback single page generation.", req.getTicketCode());
                tickets = Collections.emptyList();
            }

            byte[] pdfBytes;
            if (!tickets.isEmpty()) {
                pdfBytes = pdfService.generateTicketsPdf(tickets);
            } else {
                // Fallback for tests if ticket cannot be loaded from DB
                SpectatorTicket mockTicket = new SpectatorTicket();
                mockTicket.setTicketCode(req.getTicketCode());
                
                com.enicilion.backend.auth.entity.User user = new com.enicilion.backend.auth.entity.User();
                user.setFullName(req.getUserName());
                user.setEmail(req.getUserEmail());
                user.setWhatsapp(req.getUserPhone());
                mockTicket.setUser(user);

                com.enicilion.backend.tickets.entity.TicketTier tier = new com.enicilion.backend.tickets.entity.TicketTier();
                tier.setName(req.getTierName());
                tier.setPrice(java.math.BigDecimal.ZERO);
                mockTicket.setTier(tier);

                com.enicilion.backend.tickets.entity.Event event = new com.enicilion.backend.tickets.entity.Event();
                event.setName(req.getEventName());
                event.setLocation(req.getEventLocation());
                if (req.getEventDate() != null && !req.getEventDate().equals("TBD")) {
                    try {
                        event.setEventDate(java.time.OffsetDateTime.parse(req.getEventDate()));
                    } catch (Exception ex) {
                        event.setEventDate(java.time.OffsetDateTime.now());
                    }
                } else {
                    event.setEventDate(java.time.OffsetDateTime.now());
                }
                mockTicket.setEvent(event);

                pdfBytes = pdfService.generateTicketsPdf(Collections.singletonList(mockTicket));
            }

            String pdfBase64 = Base64.getEncoder().encodeToString(pdfBytes);

            // Build HTML email
            String html = buildHtml(req, tickets);
            String text = buildText(req, tickets);

            // Build ZeptoMail payload
            NotificationProperties.Email email = props.getEmail();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("from", Map.of(
                "address", email.getFromAddress(),
                "name",    email.getFromName()
            ));
            payload.put("to", List.of(Map.of(
                "email_address", Map.of(
                    "address", req.getUserEmail(),
                    "name",    req.getUserName() != null ? req.getUserName() : "Guest"
                )
            )));
            payload.put("subject", req.getEventName() + " — Your Tickets");
            payload.put("htmlbody", html);
            payload.put("textbody", text);
            payload.put("attachments", List.of(Map.of(
                "content",   pdfBase64,
                "mime_type", "application/pdf",
                "name",      "tickets.pdf"
            )));

            // Send
            String rawToken = email.getZeptoToken();
            String authHeader = rawToken.startsWith("Zoho-enczapikey ")
                ? rawToken
                : "Zoho-enczapikey " + rawToken;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", authHeader);

            log.info("[Email] Sending ticket to {} (count={})", req.getUserEmail(), tickets.size());
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(
                email.getZeptoApiUrl(), request, Map.class
            );
            log.info("[Email] ✅ sent to {} — status={}", req.getUserEmail(), response.getStatusCode());

        } catch (Exception e) {
            // NEVER rethrow — notification failure must NOT roll back payment
            log.error("[Email] ❌ failed for {} — {}", req.getUserEmail(), e.getMessage(), e);
        }
    }

    // ── HTML Template ────────────────────────────────────────────────────────

    private String buildHtml(TicketNotificationRequest d, List<SpectatorTicket> tickets) {
        String downloadUrl = props.getPdf().getBaseUrl() + "/ticket-pdf/" + d.getTicketCode();
        
        String tiersHtml;
        int qty;
        String amountStr = "N/A";
        String paidDateStr = "N/A";
        String methodStr = "N/A";
        
        if (!tickets.isEmpty()) {
            tiersHtml = tickets.stream()
                .map(t -> t.getTier() != null ? t.getTier().getName() : "General Admission")
                .distinct()
                .map(this::esc)
                .collect(Collectors.joining(", "));
            
            qty = tickets.size();

            SpectatorTicket first = tickets.get(0);
            if (first.getPayment() != null) {
                amountStr = first.getPayment().getAmount() + " " + first.getPayment().getCurrency();
                if (first.getPayment().getPaidAt() != null) {
                    paidDateStr = first.getPayment().getPaidAt().format(DATE_FORMATTER);
                }
                methodStr = first.getPayment().getProvider().name();
            }
        } else {
            tiersHtml = esc(d.getTierName() != null ? d.getTierName() : "General Admission");
            qty = d.getQuantity();
            amountStr = "Paid via Razorpay";
        }

        String orderId = d.getOrderId() != null ? d.getOrderId() : "N/A";

        return """
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width,initial-scale=1">
              <title>Your Ticket Confirmed</title>
            </head>
            <body style="margin:0;padding:0;background:#070707;color:#fff;font-family:Arial,Helvetica,sans-serif;">
              <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="background:#070707;">
                <tr><td align="center" style="padding:24px 12px;">
                  <table role="presentation" width="620" cellpadding="0" cellspacing="0" border="0"
                         style="width:100%%;max-width:620px;background:#0b0b0b;border:1px solid #242424;">

                    <tr><td style="background:#D4AF37;height:4px;font-size:0;">&nbsp;</td></tr>

                    <tr><td style="padding:32px 40px 8px;text-align:center;">
                      <div style="font-size:10px;letter-spacing:6px;text-transform:uppercase;color:#D4AF37;">%s</div>
                    </td></tr>

                    <tr><td style="padding:16px 40px 24px;">
                      <div style="font-size:9px;letter-spacing:5px;text-transform:uppercase;color:#D4AF37;margin-bottom:8px;">✓ TICKET CONFIRMED</div>
                      <div style="font-size:24px;font-weight:900;text-transform:uppercase;color:#fff;line-height:1.2;margin-bottom:8px;">Your Pass Has Been Issued.</div>
                      <div style="font-size:13px;color:#888;line-height:1.6;">Hi <strong style="color:#fff;">%s</strong> — your ticket is confirmed. Your PDF with QR codes is attached to this email.</div>
                    </td></tr>

                    <tr><td style="padding:0 40px 20px;">
                      <table width="100%%" cellpadding="0" cellspacing="0" border="0" style="border:1px solid #1c1c1c;">
                        <tr>
                          <td width="50%%" style="background-color: #111115; padding:14px 16px;border-right:1px solid #1c1c1c;border-bottom:1px solid #1c1c1c;vertical-align:top;">
                            <div style="font-size:8px;letter-spacing:4px;text-transform:uppercase;color:#888;margin-bottom:4px;">ATTENDEE</div>
                            <div style="font-size:13px;font-weight:700;color:#fff;">%s</div>
                          </td>
                          <td width="50%%" style="background-color: #111115; padding:14px 16px;border-bottom:1px solid #1c1c1c;vertical-align:top;">
                            <div style="font-size:8px;letter-spacing:4px;text-transform:uppercase;color:#888;margin-bottom:4px;">TICKET TYPE</div>
                            <div style="font-size:13px;font-weight:700;color:#fff;">%s</div>
                          </td>
                        </tr>
                        <tr>
                          <td width="50%%" style="background-color: #111115; padding:14px 16px;border-right:1px solid #1c1c1c;vertical-align:top;">
                            <div style="font-size:8px;letter-spacing:4px;text-transform:uppercase;color:#888;margin-bottom:4px;">DATE</div>
                            <div style="font-size:13px;font-weight:700;color:#fff;">%s</div>
                          </td>
                          <td width="50%%" style="background-color: #111115; padding:14px 16px;vertical-align:top;">
                            <div style="font-size:8px;letter-spacing:4px;text-transform:uppercase;color:#888;margin-bottom:4px;">VENUE</div>
                            <div style="font-size:13px;font-weight:700;color:#fff;">%s</div>
                          </td>
                        </tr>
                      </table>
                    </td></tr>

                    <tr><td style="padding:0 40px 20px;">
                      <div style="font-size:10px;letter-spacing:3px;text-transform:uppercase;color:#D4AF37;margin-bottom:8px;font-weight:700;">TRANSACTION DETAILS</div>
                      <table width="100%%" cellpadding="0" cellspacing="0" border="0" style="border:1px solid #1c1c1c;">
                        <tr>
                          <td width="50%%" style="background-color: #111115; padding:14px 16px;border-right:1px solid #1c1c1c;border-bottom:1px solid #1c1c1c;vertical-align:top;">
                            <div style="font-size:8px;letter-spacing:4px;text-transform:uppercase;color:#888;margin-bottom:4px;">ORDER ID</div>
                            <div style="font-size:13px;font-weight:700;color:#fff;word-break:break-all;">%s</div>
                          </td>
                          <td width="50%%" style="background-color: #111115; padding:14px 16px;border-bottom:1px solid #1c1c1c;vertical-align:top;">
                            <div style="font-size:8px;letter-spacing:4px;text-transform:uppercase;color:#888;margin-bottom:4px;">TOTAL PAID</div>
                            <div style="font-size:13px;font-weight:700;color:#D4AF37;">%s</div>
                          </td>
                        </tr>
                        <tr>
                          <td width="50%%" style="background-color: #111115; padding:14px 16px;border-right:1px solid #1c1c1c;vertical-align:top;">
                            <div style="font-size:8px;letter-spacing:4px;text-transform:uppercase;color:#888;margin-bottom:4px;">PAID DATE</div>
                            <div style="font-size:13px;font-weight:700;color:#fff;">%s</div>
                          </td>
                          <td width="50%%" style="background-color: #111115; padding:14px 16px;vertical-align:top;">
                            <div style="font-size:8px;letter-spacing:4px;text-transform:uppercase;color:#888;margin-bottom:4px;">PAYMENT METHOD</div>
                            <div style="font-size:13px;font-weight:700;color:#fff;">%s</div>
                          </td>
                        </tr>
                      </table>
                    </td></tr>

                    <tr><td style="padding:10px 40px 28px;text-align:center;">
                      <a href="%s" style="display:inline-block;background:#D4AF37;color:#000000;text-decoration:none;padding:14px 40px;font-size:10px;letter-spacing:5px;text-transform:uppercase;font-weight:700;">⬇ DOWNLOAD TICKET PDF</a>
                      <div style="font-size:10px;color:#888;margin-top:12px;">Order: %s &nbsp;·&nbsp; Qty: %d</div>
                    </td></tr>

                    <tr><td style="padding:0 40px 20px;text-align:left;">
                      <div style="font-size:9px;letter-spacing:3px;text-transform:uppercase;color:#D4AF37;margin-bottom:8px;font-weight:700;">TERMS & CONDITIONS</div>
                      <div style="font-size:9px;color:#888;line-height:1.6;font-family:Arial,sans-serif;">
                        By purchasing, downloading, presenting, or using a Motorscape 2026 ticket, the attendee agrees to these terms:<br/>
                        1. <strong>TICKET VALIDITY:</strong> Entry allowed only with valid QR ticket. Valid for one person and one-time entry only. Fake, duplicate, or altered tickets may be rejected. Do not share QR code.<br/>
                        2. <strong>ENTRY & VERIFICATION:</strong> Present at gate. Screenshot or printed copy accepted if scannable. Organizer may verify identity, age, or booking details and refuse entry for safety/fraud.<br/>
                        3. <strong>REFUNDS & CANCELLATION:</strong> Tickets are non-refundable unless cancelled by the organizer. Booking, platform, and payment gateway charges may be non-refundable. Rescheduled tickets remain valid.<br/>
                        4. <strong>TRANSFER:</strong> Non-transferable unless supported. Resale or unauthorized duplication invalidates the ticket.<br/>
                        5. <strong>SECURITY:</strong> Subject to venue security checks. Outside alcohol, weapons, fireworks, dangerous objects, or behavior lead to removal without refund.<br/>
                        6. <strong>F&B RULES:</strong> Depends on tier. Alcohol subject to legal age check. Service can be refused.<br/>
                        7. <strong>EVENT CHANGES:</strong> Schedule, zones, performers, or activities may change without notice.<br/>
                        8. <strong>MEDIA CONSENT:</strong> Consents to appear in photography/videography for event coverage and marketing.<br/>
                        9. <strong>LIABILITY & BELONGINGS:</strong> Responsible for personal items. Motorsport environments include sound, vehicles, crowds; entry is at own risk.<br/>
                        10. <strong>SUPPORT:</strong> Contact Enicilion at enicilion.com.
                      </div>
                    </td></tr>

                    <tr><td style="background:#050505;border-top:2px solid #D4AF37;padding:24px 40px;text-align:center;">
                      <div style="font-size:14px;font-weight:900;letter-spacing:4px;text-transform:uppercase;color:#fff;margin-bottom:4px;">See you there.</div>
                      <div style="font-size:10px;color:#333;">&copy; 2026 %s</div>
                    </td></tr>

                  </table>
                </td></tr>
              </table>
            </body>
            </html>
            """.formatted(
                esc(d.getEventName()),
                esc(d.getUserName()),
                esc(d.getUserName()),
                tiersHtml,
                esc(d.getEventDate() != null ? d.getEventDate() : "TBD"),
                esc(d.getEventLocation() != null ? d.getEventLocation() : "TBD"),
                esc(orderId),
                esc(amountStr),
                esc(paidDateStr),
                esc(methodStr),
                downloadUrl,
                esc(orderId),
                qty,
                esc(d.getEventName())
            );
    }

    private String buildText(TicketNotificationRequest d, List<SpectatorTicket> tickets) {
        String amountStr = "N/A";
        String paidDateStr = "N/A";
        String methodStr = "N/A";
        if (tickets != null && !tickets.isEmpty()) {
            SpectatorTicket first = tickets.get(0);
            if (first.getPayment() != null) {
                amountStr = first.getPayment().getAmount() + " " + first.getPayment().getCurrency();
                if (first.getPayment().getPaidAt() != null) {
                    paidDateStr = first.getPayment().getPaidAt().format(DATE_FORMATTER);
                }
                methodStr = first.getPayment().getProvider().name();
            }
        } else {
            amountStr = "Paid via Razorpay";
        }

        return String.join("\n",
            "Ticket Confirmed — " + d.getEventName(),
            "",
            "Attendee: " + d.getUserName(),
            "Ticket Type: " + (d.getTierName() != null ? d.getTierName() : "General Admission"),
            "Date: " + d.getEventDate(),
            "Venue: " + d.getEventLocation(),
            "",
            "TRANSACTION DETAILS",
            "Order ID: " + d.getOrderId(),
            "Total Paid: " + amountStr,
            "Paid Date: " + paidDateStr,
            "Payment Method: " + methodStr,
            "",
            "Download Link: " + props.getPdf().getBaseUrl() + "/ticket-pdf/" + d.getTicketCode(),
            "",
            "TERMS & CONDITIONS",
            "By purchasing, downloading, presenting, or using a Motorscape 2026 ticket, the attendee agrees to these terms:",
            "1. TICKET VALIDITY: Entry allowed only with valid QR ticket. Valid for one person and one-time entry only. Fake, duplicate, or altered tickets may be rejected. Do not share QR code.",
            "2. ENTRY & VERIFICATION: Present at gate. Screenshot or printed copy accepted if scannable. Organizer may verify identity, age, or booking details and refuse entry for safety/fraud.",
            "3. REFUNDS & CANCELLATION: Tickets are non-refundable unless cancelled by the organizer. Booking, platform, and payment gateway charges may be non-refundable. Rescheduled tickets remain valid.",
            "4. TRANSFER: Non-transferable unless supported. Resale or unauthorized duplication invalidates the ticket.",
            "5. SECURITY: Subject to venue security checks. Outside alcohol, weapons, fireworks, dangerous objects, or behavior lead to removal without refund.",
            "6. F&B RULES: Depends on tier. Alcohol subject to legal age check. Service can be refused.",
            "7. EVENT CHANGES: Schedule, zones, performers, or activities may change without notice.",
            "8. MEDIA CONSENT: Consents to appear in photography/videography for event coverage and marketing.",
            "9. LIABILITY & BELONGINGS: Responsible for personal items. Motorsport environments include sound, vehicles, crowds; entry is at own risk.",
            "10. SUPPORT: Contact Enicilion at enicilion.com."
        );
    }

    private String esc(String v) {
        if (v == null) return "";
        return v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}

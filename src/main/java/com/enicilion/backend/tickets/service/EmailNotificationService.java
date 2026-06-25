package com.enicilion.backend.tickets.service;

import com.enicilion.backend.tickets.entity.SpectatorTicket;
import com.enicilion.backend.auth.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import com.enicilion.backend.tickets.repository.TicketRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * ZeptoMail transactional email service.
 * Sends e-ticket confirmation emails with the PDF as a Base64 attachment.
 * Spec §5.2: triggered after successful Razorpay payment verification.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailNotificationService {

    private static final java.time.format.DateTimeFormatter DATE_FORMATTER = java.time.format.DateTimeFormatter
            .ofPattern("dd MMM yyyy, hh:mm a")
            .withZone(java.time.ZoneId.of("Asia/Kolkata"));

    @Value("${app.zeptomail.api-key}")
    private String apiKey;

    @Value("${app.zeptomail.sender-email}")
    private String senderEmail;

    @Value("${app.frontend-url:http://localhost:4000}")
    private String frontendUrl;

    private final PdfService pdfService;
    private final RestTemplate restTemplate;
    private final TicketRepository ticketRepository;

    private static final String ZEPTO_API_URL = "https://api.zeptomail.in/v1.1/email";

    /**
     * Send the ticket confirmation email with a PDF attachment asynchronously
     * so it does not block the HTTP payment-verification response.
     */
    @Async
    @Transactional(readOnly = true)
    public void sendTicketConfirmationEmail(List<SpectatorTicket> tickets) {
        if (tickets == null || tickets.isEmpty()) return;

        try {
            // Eagerly reload tickets with all details using their codes to prevent LazyInitializationException in the async thread.
            List<String> codes = tickets.stream()
                    .map(SpectatorTicket::getTicketCode)
                    .collect(Collectors.toList());
            List<SpectatorTicket> reloadedTickets = ticketRepository.findByTicketCodeIn(codes);
            if (reloadedTickets.isEmpty()) {
                log.warn("[EmailNotification] Eager reload failed — no tickets found for codes {}", codes);
                return;
            }

            SpectatorTicket first = reloadedTickets.get(0);
            if (first.getUser() == null) {
                log.warn("[EmailNotification] Eager reload failed — no user associated with ticket code {}", first.getTicketCode());
                return;
            }
            String recipientEmail = first.getUser().getEmail();
            String recipientName  = first.getUser().getFullName();

            // Generate consolidated PDF covering all tickets in this payment
            byte[] pdfBytes = pdfService.generateTicketsPdf(reloadedTickets);
            String pdfBase64 = Base64.getEncoder().encodeToString(pdfBytes);

            String htmlBody = buildEmailHtml(reloadedTickets);

            // Compose ZeptoMail JSON payload
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("from", Map.of("address", senderEmail, "name", "Motorscape Tickets"));
            payload.put("to", List.of(Map.of("email_address", Map.of("address", recipientEmail, "name", recipientName))));
            payload.put("subject", "🎟 Your Motorscape 2026 Tickets Are Confirmed!");
            payload.put("htmlbody", htmlBody);
            payload.put("attachments", List.of(Map.of(
                "name",    "Motorscape_Ticket.pdf",
                "content", pdfBase64,
                "mime_type", "application/pdf"
            )));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Zoho-enczapikey " + apiKey);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    ZEPTO_API_URL, HttpMethod.POST, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Ticket confirmation email sent to {} for {} tickets", recipientEmail, reloadedTickets.size());
            } else {
                log.warn("ZeptoMail returned non-2xx status {} for {}", response.getStatusCode(), recipientEmail);
            }

        } catch (Exception e) {
            // Non-fatal – log and continue. Ticket is already paid.
            log.error("Failed to send ticket confirmation email: {}", e.getMessage(), e);
        }
    }

    /**
     * Send the email verification link asynchronously.
     */
    @Async
    public void sendVerificationEmail(User user) {
        if (user == null || user.getEmail() == null) return;

        String recipientEmail = user.getEmail();
        String recipientName = user.getFullName() != null ? user.getFullName() : "User";
        String token = user.getEmailVerificationToken();

        try {
            String verificationUrl = frontendUrl + "/verify-email.html?token=" + token;
            String htmlBody = buildVerificationEmailHtml(recipientName, verificationUrl);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("from", Map.of("address", senderEmail, "name", "Motorscape"));
            payload.put("to", List.of(Map.of("email_address", Map.of("address", recipientEmail, "name", recipientName))));
            payload.put("subject", "✉️ Verify your Motorscape Email Address");
            payload.put("htmlbody", htmlBody);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Zoho-enczapikey " + apiKey);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    "https://api.zeptomail.in/v1.1/email", HttpMethod.POST, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Verification email successfully sent to {}", recipientEmail);
            } else {
                log.warn("ZeptoMail returned non-2xx status {} for verification email to {}", response.getStatusCode(), recipientEmail);
            }
        } catch (Exception e) {
            log.error("Failed to send verification email to {}: {}", recipientEmail, e.getMessage(), e);
        }
    }

    private String buildEmailHtml(List<SpectatorTicket> tickets) {
        SpectatorTicket first = tickets.get(0);
        String name = first.getUser().getFullName();
        String eventName = first.getEvent().getName();
        String eventDate = first.getEvent().getEventDate() != null ? first.getEvent().getEventDate().format(DATE_FORMATTER) : "TBD";
        String eventLocation = first.getEvent().getLocation() != null ? first.getEvent().getLocation() : "TBD";
        
        String tiersHtml = tickets.stream()
            .map(t -> t.getTier() != null ? t.getTier().getName() : "General Admission")
            .distinct()
            .map(this::esc)
            .collect(Collectors.joining(", "));
        
        int qty = tickets.size();
        String amountStr = "N/A";
        String paidDateStr = "N/A";
        String methodStr = "N/A";
        String orderId = "N/A";

        if (first.getPayment() != null) {
            amountStr = first.getPayment().getAmount() + " " + first.getPayment().getCurrency();
            if (first.getPayment().getPaidAt() != null) {
                paidDateStr = first.getPayment().getPaidAt().format(DATE_FORMATTER);
            }
            methodStr = first.getPayment().getProvider().name();
            orderId = first.getPayment().getId().toString();
        }

        String downloadUrl = frontendUrl + "/api/ticket-pdf/" + first.getTicketCode();

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
                esc(eventName),
                esc(name),
                esc(name),
                tiersHtml,
                esc(eventDate),
                esc(eventLocation),
                esc(orderId),
                esc(amountStr),
                esc(paidDateStr),
                esc(methodStr),
                downloadUrl,
                esc(orderId),
                qty,
                esc(eventName)
            );
    }

    private String buildVerificationEmailHtml(String name, String verificationUrl) {
        return "<!DOCTYPE html><html><head><meta charset='UTF-8'></head>"
            + "<body style='background:#0C0C0C;color:#f0f0f0;font-family:Helvetica,Arial,sans-serif;padding:32px'>"
            + "<div style='max-width:560px;margin:0 auto;background:#141414;padding:32px;border-radius:8px;border:1px solid #222'>"
            + "<h1 style='color:#C9A84C;letter-spacing:2px;font-size:22px;margin-top:0'>MOTORSCAPE 2026</h1>"
            + "<p style='font-size:16px'>Hi " + name + ",</p>"
            + "<p>Thank you for registering. Please click the button below to verify your email address and activate your account:</p>"
            + "<div style='margin:32px 0;text-align:center'>"
            + "<a href='" + verificationUrl + "' style='background:#C9A84C;color:#0C0C0C;padding:12px 24px;border-radius:4px;text-decoration:none;font-weight:bold;display:inline-block'>Verify Email Address</a>"
            + "</div>"
            + "<p style='font-size:14px;color:#888'>If the button doesn't work, you can also copy and paste the following link into your browser:</p>"
            + "<p style='font-size:12px;color:#C9A84C;word-break:break-all'>" + verificationUrl + "</p>"
            + "<p style='font-size:12px;color:#888;margin-top:24px'>This link will expire in 24 hours.</p>"
            + "<hr style='border-color:#222;margin:32px 0'>"
            + "<p style='color:#555;font-size:11px'>© 2026 Enicilion / Motorscape. All rights reserved.</p>"
            + "</div></body></html>";
    }

    private String esc(String v) {
        if (v == null) return "";
        return v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}

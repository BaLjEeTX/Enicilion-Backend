package com.enicilion.backend.tickets.controller;

import com.enicilion.backend.auth.entity.User;
import com.enicilion.backend.auth.repository.UserRepository;
import com.enicilion.backend.common.dto.ApiResponse;
import com.enicilion.backend.common.exception.ResourceNotFoundException;
import com.enicilion.backend.common.exception.UnauthorizedException;
import com.enicilion.backend.common.service.SecureTokenService;
import com.enicilion.backend.auth.service.SecurityContextService;
import com.enicilion.backend.tickets.dto.CheckoutRequest;
import com.enicilion.backend.tickets.dto.PaymentResponse;
import com.enicilion.backend.tickets.dto.ScanResponse;
import com.enicilion.backend.tickets.dto.ScanTicketRequest;
import com.enicilion.backend.tickets.entity.SpectatorTicket;
import com.enicilion.backend.tickets.repository.TicketRepository;
import com.enicilion.backend.tickets.service.CheckoutService;
import com.enicilion.backend.tickets.service.PdfService;
import com.enicilion.backend.tickets.service.ScanService;
import com.enicilion.backend.wallet.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.enicilion.backend.common.exception.BadValidationException;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.context.SecurityContextHolder;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final CheckoutService checkoutService;
    private final ScanService scanService;
    private final PdfService pdfService;
    private final WalletService walletService;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final SecureTokenService secureTokenService;
    private final SecurityContextService securityContextService;

    @PostMapping("/checkout")
    public ResponseEntity<ApiResponse<PaymentResponse>> checkout(
            @Valid @RequestBody CheckoutRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader) {
        
        User user = null;
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        if (email != null && !email.equals("anonymousUser")) {
            user = userRepository.findByEmail(email).orElse(null);
        }
        
        PaymentResponse response = checkoutService.checkout(request, user, idempotencyKeyHeader);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @PostMapping("/scan")
    public ResponseEntity<ApiResponse<ScanResponse>> scanTicket(
            @Valid @RequestBody ScanTicketRequest request) {
        
        ScanResponse response = scanService.scanTicket(request.getCode(), request.getPassword());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/revert-checkin")
    public ResponseEntity<ApiResponse<ScanResponse>> revertCheckin(
            @Valid @RequestBody ScanTicketRequest request) {
        
        ScanResponse response = scanService.revertCheckin(request.getCode(), request.getPassword());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/pdf/{code}")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> getTicketPdf(
            @PathVariable("code") String code,
            @RequestParam(value = "token", required = false) String token) {
        SpectatorTicket ticket = ticketRepository.findByTicketCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found with code: " + code));

        if (!verifyTicketAccess(ticket, token)) {
            throw new UnauthorizedException("Access denied to download PDF for ticket code: " + code);
        }

        List<SpectatorTicket> tickets;
        if (ticket.getPayment() != null) {
            tickets = ticketRepository.findByPaymentId(ticket.getPayment().getId());
            // Sort by ticket code to keep the order deterministic
            tickets.sort(java.util.Comparator.comparing(SpectatorTicket::getTicketCode));
        } else {
            tickets = List.of(ticket);
        }

        byte[] pdfBytes = pdfService.generateTicketsPdf(tickets);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(
                ContentDisposition.builder("attachment")
                        .filename("tickets_" + code + ".pdf")
                        .build()
        );

        return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
    }

    @GetMapping("/payment/{paymentId}/pdf")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> getTicketsPdfByPayment(
            @PathVariable("paymentId") UUID paymentId,
            @RequestParam(value = "token", required = false) String token) {
        List<SpectatorTicket> tickets = ticketRepository.findByPaymentId(paymentId);
        if (tickets.isEmpty()) {
            throw new ResourceNotFoundException("No tickets found for this payment: " + paymentId);
        }

        if (!verifyPaymentAccess(paymentId, token)) {
            throw new UnauthorizedException("Access denied to download PDF for payment: " + paymentId);
        }

        boolean anyPaid = tickets.stream().anyMatch(t -> 
            t.getStatus() == com.enicilion.backend.tickets.entity.TicketStatus.paid || 
            t.getStatus() == com.enicilion.backend.tickets.entity.TicketStatus.checked_in
        );

        if (!anyPaid) {
            throw new BadValidationException("Payment is pending or failed. Tickets cannot be downloaded yet.");
        }

        tickets.sort(java.util.Comparator.comparing(SpectatorTicket::getTicketCode));

        byte[] pdfBytes = pdfService.generateTicketsPdf(tickets);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(
                ContentDisposition.builder("attachment")
                        .filename("tickets_" + paymentId + ".pdf")
                        .build()
        );

        return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getMyTickets() {
        User user = securityContextService.getCurrentUser();
        List<SpectatorTicket> tickets = ticketRepository.findByUserId(user.getId());
        List<Map<String, Object>> responseList = new ArrayList<>();
        for (SpectatorTicket t : tickets) {
            Map<String, Object> map = new HashMap<>();
            map.put("code", t.getTicketCode());
            map.put("tierName", t.getTier() != null ? t.getTier().getName() : "General Admission");
            map.put("status", t.getStatus().name());
            responseList.add(map);
        }
        return ResponseEntity.ok(ApiResponse.success(responseList));
    }

    @GetMapping("/{code}/apple-wallet")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> getAppleWalletPass(
            @PathVariable("code") String code,
            @RequestParam(value = "token", required = false) String token) {
        SpectatorTicket ticket = ticketRepository.findByTicketCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));
        
        if (!verifyTicketAccess(ticket, token)) {
            throw new UnauthorizedException("Access denied to Apple Wallet pass for ticket code: " + code);
        }
        
        byte[] passBytes = walletService.generateApplePass(ticket);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.apple.pkpass"));
        headers.setContentDisposition(
                ContentDisposition.builder("attachment")
                        .filename("ticket_" + code + ".pkpass")
                        .build()
        );
        
        return new ResponseEntity<>(passBytes, headers, HttpStatus.OK);
    }

    @GetMapping("/{code}/google-wallet")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<Map<String, String>>> getGoogleWalletLink(
            @PathVariable("code") String code,
            @RequestParam(value = "token", required = false) String token) {
        SpectatorTicket ticket = ticketRepository.findByTicketCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));
        
        if (!verifyTicketAccess(ticket, token)) {
            throw new UnauthorizedException("Access denied to Google Wallet link for ticket code: " + code);
        }
        
        String jwt = walletService.generateGoogleWalletJwt(ticket);
        String saveUrl = "https://pay.google.com/gp/v/save/" + jwt;
        
        return ResponseEntity.ok(ApiResponse.success(Map.of("saveUrl", saveUrl)));
    }

    private boolean verifyTicketAccess(SpectatorTicket ticket, String token) {
        String authEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        if (authEmail != null && !authEmail.equals("anonymousUser")) {
            if (ticket.getUser() != null && authEmail.equalsIgnoreCase(ticket.getUser().getEmail())) {
                return true;
            }
        }
        if (token != null && !token.isEmpty()) {
        return token.equalsIgnoreCase(secureTokenService.generateTicketToken(ticket.getTicketCode(), ticket.getUser() != null ? ticket.getUser().getEmail() : ""));
        }
        return false;
    }

    private boolean verifyPaymentAccess(UUID paymentId, String token) {
        List<SpectatorTicket> tickets = ticketRepository.findByPaymentId(paymentId);
        if (tickets.isEmpty()) {
            return false;
        }
        String authEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        if (authEmail != null && !authEmail.equals("anonymousUser")) {
            SpectatorTicket sample = tickets.get(0);
            if (sample.getUser() != null && authEmail.equalsIgnoreCase(sample.getUser().getEmail())) {
                return true;
            }
        }
        if (token != null && !token.isEmpty()) {
            return token.equalsIgnoreCase(secureTokenService.generatePaymentToken(paymentId));
        }
        return false;
    }

}

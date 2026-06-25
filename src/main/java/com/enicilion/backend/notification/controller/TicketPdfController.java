package com.enicilion.backend.notification.controller;

import com.enicilion.backend.common.exception.ResourceNotFoundException;
import com.enicilion.backend.tickets.entity.SpectatorTicket;
import com.enicilion.backend.tickets.repository.TicketRepository;
import com.enicilion.backend.tickets.service.PdfService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/ticket-pdf")
@RequiredArgsConstructor
public class TicketPdfController {

    private final TicketRepository ticketRepository;
    private final PdfService pdfService;

    @GetMapping("/{ticketCode}")
    public ResponseEntity<byte[]> getPublicTicketPdf(@PathVariable("ticketCode") String ticketCode) {
        SpectatorTicket ticket = ticketRepository.findByTicketCodeWithDetails(ticketCode)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketCode));

        List<SpectatorTicket> tickets;
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

        byte[] pdfBytes = pdfService.generateTicketsPdf(tickets);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(
                ContentDisposition.builder("inline")
                        .filename(ticketCode + ".pdf")
                        .build()
        );

        log.info("[PdfController] Serving public PDF for ticketCode={} (count={})", ticketCode, tickets.size());
        return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
    }
}

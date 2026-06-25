package com.enicilion.backend.notification.service;

import com.enicilion.backend.notification.dto.TicketNotificationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TicketPdfServiceTest {

    private TicketPdfService pdfService;

    @BeforeEach
    void setUp() {
        pdfService = new TicketPdfService();
    }

    @Test
    void testGenerateTicketPdf_Success() {
        TicketNotificationRequest data = TicketNotificationRequest.builder()
                .userName("John Doe")
                .userEmail("john@example.com")
                .userPhone("9876543210")
                .ticketCode("PASS-ABC123")
                .eventName("Enicilion Festival")
                .eventDate("Saturday, June 13, 2026")
                .eventLocation("Mohali")
                .orderId("order_xyz")
                .quantity(1)
                .tierName("Spectator Pass")
                .build();

        byte[] pdfBytes = pdfService.generateTicketPdf(data);
        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);
    }

    @Test
    void testGenerateTicketPdf_HandlesNullsGracefully() {
        TicketNotificationRequest data = TicketNotificationRequest.builder()
                .ticketCode("PASS-XYZ")
                .build();

        byte[] pdfBytes = pdfService.generateTicketPdf(data);
        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);
    }
}

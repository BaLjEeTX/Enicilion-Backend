package com.enicilion.backend.notification.service;

import com.enicilion.backend.config.NotificationProperties;
import com.enicilion.backend.notification.dto.TicketNotificationRequest;
import com.enicilion.backend.tickets.entity.SpectatorTicket;
import com.enicilion.backend.tickets.repository.TicketRepository;
import com.enicilion.backend.tickets.service.PdfService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class EmailServiceTest {

    @Mock
    private NotificationProperties props;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private PdfService pdfService;

    @InjectMocks
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        NotificationProperties.Email emailProps = new NotificationProperties.Email();
        emailProps.setZeptoApiUrl("https://api.zeptomail.in/v1.1/email");
        emailProps.setZeptoToken("test_token");
        emailProps.setFromAddress("noreply@enicilion.com");
        emailProps.setFromName("Enicilion");

        NotificationProperties.Pdf pdfProps = new NotificationProperties.Pdf();
        pdfProps.setBaseUrl("http://localhost:4000/api");

        when(props.getEmail()).thenReturn(emailProps);
        when(props.getPdf()).thenReturn(pdfProps);
    }

    @Test
    void testSendTicketConfirmationEmail_Success() {
        TicketNotificationRequest req = TicketNotificationRequest.builder()
                .userName("John Doe")
                .userEmail("john@example.com")
                .ticketCode("PASS-123")
                .eventName("Enicilion Festival")
                .tierName("General Admission")
                .quantity(1)
                .orderId("order_1")
                .build();

        // Stub repository to return empty optional, triggering the test fallback code
        when(ticketRepository.findByTicketCodeWithDetails(anyString())).thenReturn(Optional.empty());

        byte[] mockPdfBytes = new byte[]{1, 2, 3};
        when(pdfService.generateTicketsPdf(anyList())).thenReturn(mockPdfBytes);

        ResponseEntity<Map> mockResponse = new ResponseEntity<>(Map.of("message", "success"), HttpStatus.OK);
        when(restTemplate.postForEntity(any(String.class), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(mockResponse);

        emailService.sendTicketConfirmationEmail(req);

        ArgumentCaptor<HttpEntity> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate, times(1)).postForEntity(
                eq("https://api.zeptomail.in/v1.1/email"),
                requestCaptor.capture(),
                eq(Map.class)
        );

        HttpEntity<Map<String, Object>> capturedRequest = requestCaptor.getValue();
        assertNotNull(capturedRequest);
        Map<String, Object> body = capturedRequest.getBody();
        assertNotNull(body);
        assertEquals("Enicilion Festival — Your Tickets", body.get("subject"));
    }

    @Test
    void testSendTicketConfirmationEmail_EmptyEmail_DoesNotCallApi() {
        TicketNotificationRequest req = TicketNotificationRequest.builder()
                .userName("John Doe")
                .userEmail("")
                .ticketCode("PASS-123")
                .build();

        emailService.sendTicketConfirmationEmail(req);

        verify(restTemplate, never()).postForEntity(any(String.class), any(HttpEntity.class), eq(Map.class));
    }
}

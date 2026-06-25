package com.enicilion.backend.notification.service;

import com.enicilion.backend.config.NotificationProperties;
import com.enicilion.backend.notification.dto.TicketNotificationRequest;
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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class WhatsAppServiceTest {

    @Mock
    private NotificationProperties props;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private WhatsAppService whatsAppService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Setup notification properties mocks
        NotificationProperties.WhatsApp wa = new NotificationProperties.WhatsApp();
        wa.setApiUrl("https://graph.facebook.com/v17.0");
        wa.setPhoneNumberId("12345");
        wa.setToken("test_token");
        wa.setTemplateName("enc_ticket_confirmed_v2");

        NotificationProperties.Pdf pdf = new NotificationProperties.Pdf();
        pdf.setBaseUrl("http://localhost:4000/api");

        when(props.getWhatsapp()).thenReturn(wa);
        when(props.getPdf()).thenReturn(pdf);
    }

    @Test
    void testSendTicketConfirmed_Success() {
        TicketNotificationRequest req = TicketNotificationRequest.builder()
                .userName("John Doe")
                .userPhone("9876543210")
                .ticketCode("PASS-123")
                .tierName("VIP")
                .orderId("order_1")
                .build();

        ResponseEntity<Map> mockResponse = new ResponseEntity<>(Map.of("messaging_product", "whatsapp"), HttpStatus.OK);
        when(restTemplate.postForEntity(any(String.class), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(mockResponse);

        whatsAppService.sendTicketConfirmed(req);

        ArgumentCaptor<HttpEntity> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate, times(1)).postForEntity(
                eq("https://graph.facebook.com/v17.0/12345/messages"),
                requestCaptor.capture(),
                eq(Map.class)
        );

        HttpEntity<Map<String, Object>> capturedRequest = requestCaptor.getValue();
        assertNotNull(capturedRequest);
        Map<String, Object> body = capturedRequest.getBody();
        assertNotNull(body);
        assertEquals("whatsapp", body.get("messaging_product"));
        assertEquals("919876543210", body.get("to")); // Normalized
    }

    @Test
    void testSendTicketConfirmed_EmptyPhone_DoesNotCallApi() {
        TicketNotificationRequest req = TicketNotificationRequest.builder()
                .userName("John Doe")
                .userPhone("")
                .ticketCode("PASS-123")
                .build();

        whatsAppService.sendTicketConfirmed(req);

        verify(restTemplate, never()).postForEntity(any(String.class), any(HttpEntity.class), eq(Map.class));
    }
}

package com.enicilion.backend.notification.controller;

import com.enicilion.backend.common.dto.ApiResponse;
import com.enicilion.backend.common.exception.ResourceNotFoundException;
import com.enicilion.backend.notification.service.NotificationService;
import com.enicilion.backend.tickets.entity.SpectatorTicket;
import com.enicilion.backend.tickets.repository.TicketRepository;
import com.enicilion.backend.auth.entity.User;
import com.enicilion.backend.tickets.entity.Event;
import com.enicilion.backend.tickets.entity.TicketTier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminNotificationControllerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private TicketRepository ticketRepository;

    @InjectMocks
    private AdminNotificationController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testSendTicket_Success_WithDefaults() {
        String ticketCode = "PASS-ABC123";
        AdminNotificationController.SendTicketRequest requestBody = new AdminNotificationController.SendTicketRequest();
        requestBody.setTicketCode(ticketCode);

        User user = new User();
        user.setFullName("John Doe");
        user.setEmail("john@example.com");
        user.setWhatsapp("9876543210");

        Event event = new Event();
        event.setName("Enicilion Fest");

        TicketTier tier = new TicketTier();
        tier.setName("VIP Pass");

        SpectatorTicket ticket = new SpectatorTicket();
        ticket.setTicketCode(ticketCode);
        ticket.setUser(user);
        ticket.setEvent(event);
        ticket.setTier(tier);

        when(ticketRepository.findByTicketCodeWithDetails(ticketCode)).thenReturn(Optional.of(ticket));

        ResponseEntity<ApiResponse<Map<String, Object>>> response = controller.sendTicket(requestBody);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());

        verify(notificationService, times(1)).queueTicketConfirmation(eq(ticketCode), eq(null), eq(null));
    }

    @Test
    void testSendTicket_Success_WithOverrides() {
        String ticketCode = "PASS-ABC123";
        AdminNotificationController.SendTicketRequest requestBody = new AdminNotificationController.SendTicketRequest();
        requestBody.setTicketCode(ticketCode);
        requestBody.setOverrideEmail("override@test.com");
        requestBody.setOverridePhone("9999999999");

        User user = new User();
        user.setFullName("John Doe");
        user.setEmail("john@example.com");
        user.setWhatsapp("9876543210");

        Event event = new Event();
        event.setName("Enicilion Fest");

        SpectatorTicket ticket = new SpectatorTicket();
        ticket.setTicketCode(ticketCode);
        ticket.setUser(user);
        ticket.setEvent(event);

        when(ticketRepository.findByTicketCodeWithDetails(ticketCode)).thenReturn(Optional.of(ticket));

        ResponseEntity<ApiResponse<Map<String, Object>>> response = controller.sendTicket(requestBody);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        verify(notificationService, times(1)).queueTicketConfirmation(eq(ticketCode), eq("override@test.com"), eq("9999999999"));
    }

    @Test
    void testSendTicket_MissingTicketCode_ReturnsBadRequest() {
        AdminNotificationController.SendTicketRequest requestBody = new AdminNotificationController.SendTicketRequest();

        ResponseEntity<ApiResponse<Map<String, Object>>> response = controller.sendTicket(requestBody);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(notificationService, never()).queueTicketConfirmation(any(), any(), any());
    }

    @Test
    void testSendTicket_TicketNotFound_ThrowsResourceNotFoundException() {
        String ticketCode = "INVALID";
        AdminNotificationController.SendTicketRequest requestBody = new AdminNotificationController.SendTicketRequest();
        requestBody.setTicketCode(ticketCode);

        when(ticketRepository.findByTicketCodeWithDetails(ticketCode)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> controller.sendTicket(requestBody));
        verify(notificationService, never()).queueTicketConfirmation(any(), any(), any());
    }
}

package com.enicilion.backend.notification.service;

import com.enicilion.backend.notification.entity.NotificationOutbox;
import com.enicilion.backend.notification.entity.OutboxStatus;
import com.enicilion.backend.notification.repository.NotificationOutboxRepository;
import com.enicilion.backend.tickets.entity.SpectatorTicket;
import com.enicilion.backend.tickets.repository.TicketRepository;
import com.enicilion.backend.auth.entity.User;
import com.enicilion.backend.tickets.entity.Event;
import com.enicilion.backend.tickets.entity.TicketTier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class NotificationOutboxWorkerTest {

    @Mock
    private NotificationOutboxRepository outboxRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private WhatsAppService whatsAppService;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private NotificationOutboxWorker worker;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testProcessOutbox_Success() {
        String ticketCode = "PASS-123";
        NotificationOutbox task = NotificationOutbox.builder()
                .ticketCode(ticketCode)
                .status(OutboxStatus.PENDING)
                .attempts(0)
                .scheduledAt(OffsetDateTime.now())
                .build();

        User user = new User();
        user.setFullName("John Doe");
        user.setEmail("john@example.com");
        user.setWhatsapp("9876543210");

        Event event = new Event();
        event.setName("Enicilion");

        TicketTier tier = new TicketTier();
        tier.setName("General");

        SpectatorTicket ticket = new SpectatorTicket();
        ticket.setTicketCode(ticketCode);
        ticket.setUser(user);
        ticket.setEvent(event);
        ticket.setTier(tier);

        when(outboxRepository.findPendingTasks(eq(OutboxStatus.PENDING), any(), eq(3), any(Pageable.class)))
                .thenReturn(List.of(task));
        when(ticketRepository.findByTicketCodeWithDetails(ticketCode)).thenReturn(Optional.of(ticket));

        worker.processOutbox();

        verify(whatsAppService, times(1)).sendTicketConfirmed(any());
        verify(emailService, times(1)).sendTicketConfirmationEmail(any());

        ArgumentCaptor<NotificationOutbox> captor = ArgumentCaptor.forClass(NotificationOutbox.class);
        verify(outboxRepository, times(1)).save(captor.capture());

        NotificationOutbox saved = captor.getValue();
        assertEquals(OutboxStatus.SENT, saved.getStatus());
        assertEquals(1, saved.getAttempts());
        assertNull(saved.getErrorMessage());
    }

    @Test
    void testProcessOutbox_TicketNotFound() {
        String ticketCode = "INVALID";
        NotificationOutbox task = NotificationOutbox.builder()
                .ticketCode(ticketCode)
                .status(OutboxStatus.PENDING)
                .attempts(0)
                .scheduledAt(OffsetDateTime.now())
                .build();

        when(outboxRepository.findPendingTasks(eq(OutboxStatus.PENDING), any(), eq(3), any(Pageable.class)))
                .thenReturn(List.of(task));
        when(ticketRepository.findByTicketCodeWithDetails(ticketCode)).thenReturn(Optional.empty());

        worker.processOutbox();

        verify(whatsAppService, never()).sendTicketConfirmed(any());
        verify(emailService, never()).sendTicketConfirmationEmail(any());

        ArgumentCaptor<NotificationOutbox> captor = ArgumentCaptor.forClass(NotificationOutbox.class);
        verify(outboxRepository, times(1)).save(captor.capture());

        NotificationOutbox saved = captor.getValue();
        assertEquals(OutboxStatus.FAILED, saved.getStatus());
        assertEquals("Ticket not found in database", saved.getErrorMessage());
    }

    @Test
    void testProcessOutbox_DispatchFailure_Retries() {
        String ticketCode = "PASS-123";
        NotificationOutbox task = NotificationOutbox.builder()
                .ticketCode(ticketCode)
                .status(OutboxStatus.PENDING)
                .attempts(0)
                .scheduledAt(OffsetDateTime.now())
                .build();

        User user = new User();
        user.setFullName("John Doe");
        user.setEmail("john@example.com");
        user.setWhatsapp("9876543210");

        Event event = new Event();
        event.setName("Enicilion");

        SpectatorTicket ticket = new SpectatorTicket();
        ticket.setTicketCode(ticketCode);
        ticket.setUser(user);
        ticket.setEvent(event);

        when(outboxRepository.findPendingTasks(eq(OutboxStatus.PENDING), any(), eq(3), any(Pageable.class)))
                .thenReturn(List.of(task));
        when(ticketRepository.findByTicketCodeWithDetails(ticketCode)).thenReturn(Optional.of(ticket));

        doThrow(new RuntimeException("API Connection Failed")).when(whatsAppService).sendTicketConfirmed(any());

        worker.processOutbox();

        ArgumentCaptor<NotificationOutbox> captor = ArgumentCaptor.forClass(NotificationOutbox.class);
        verify(outboxRepository, times(1)).save(captor.capture());

        NotificationOutbox saved = captor.getValue();
        assertEquals(OutboxStatus.PENDING, saved.getStatus()); // Stays PENDING
        assertEquals(1, saved.getAttempts());
        assertEquals("API Connection Failed", saved.getErrorMessage());
        assertTrue(saved.getScheduledAt().isAfter(OffsetDateTime.now().plusMinutes(1)));
    }

    @Test
    void testProcessOutbox_DispatchFailure_ExceedsMaxAttempts() {
        String ticketCode = "PASS-123";
        NotificationOutbox task = NotificationOutbox.builder()
                .ticketCode(ticketCode)
                .status(OutboxStatus.PENDING)
                .attempts(2) // already failed twice
                .scheduledAt(OffsetDateTime.now())
                .build();

        User user = new User();
        user.setFullName("John Doe");
        user.setEmail("john@example.com");
        user.setWhatsapp("9876543210");

        Event event = new Event();
        event.setName("Enicilion");

        SpectatorTicket ticket = new SpectatorTicket();
        ticket.setTicketCode(ticketCode);
        ticket.setUser(user);
        ticket.setEvent(event);

        when(outboxRepository.findPendingTasks(eq(OutboxStatus.PENDING), any(), eq(3), any(Pageable.class)))
                .thenReturn(List.of(task));
        when(ticketRepository.findByTicketCodeWithDetails(ticketCode)).thenReturn(Optional.of(ticket));

        doThrow(new RuntimeException(" ZeptoMail Limit Exceeded")).when(emailService).sendTicketConfirmationEmail(any());

        worker.processOutbox();

        ArgumentCaptor<NotificationOutbox> captor = ArgumentCaptor.forClass(NotificationOutbox.class);
        verify(outboxRepository, times(1)).save(captor.capture());

        NotificationOutbox saved = captor.getValue();
        assertEquals(OutboxStatus.FAILED, saved.getStatus()); // Transitions to FAILED
        assertEquals(3, saved.getAttempts());
        assertEquals(" ZeptoMail Limit Exceeded", saved.getErrorMessage());
    }
}

package com.enicilion.backend.notification.service;

import com.enicilion.backend.notification.entity.NotificationOutbox;
import com.enicilion.backend.notification.entity.OutboxStatus;
import com.enicilion.backend.notification.repository.NotificationOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationServiceTest {

    @Mock
    private NotificationOutboxRepository outboxRepository;

    @InjectMocks
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testQueueTicketConfirmation_SavesPendingTask() {
        String ticketCode = "PASS-ABC123";
        String email = "test@example.com";
        String phone = "9876543210";

        notificationService.queueTicketConfirmation(ticketCode, email, phone);

        ArgumentCaptor<NotificationOutbox> captor = ArgumentCaptor.forClass(NotificationOutbox.class);
        verify(outboxRepository, times(1)).save(captor.capture());

        NotificationOutbox saved = captor.getValue();
        assertNotNull(saved);
        assertEquals(ticketCode, saved.getTicketCode());
        assertEquals(email, saved.getOverrideEmail());
        assertEquals(phone, saved.getOverridePhone());
        assertEquals(OutboxStatus.PENDING, saved.getStatus());
        assertEquals(0, saved.getAttempts());
        assertNotNull(saved.getScheduledAt());
    }
}

package com.enicilion.backend.tickets.service;

import com.enicilion.backend.auth.entity.User;
import com.enicilion.backend.tickets.dto.ScanResponse;
import com.enicilion.backend.tickets.entity.*;
import com.enicilion.backend.tickets.repository.CheckinRepository;
import com.enicilion.backend.tickets.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ScanServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private CheckinRepository checkinRepository;

    @Mock
    private FirebaseSyncService firebaseSyncService;

    @Mock
    private LiveCheckinService liveCheckinService;

    @InjectMocks
    private ScanService scanService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        org.springframework.test.util.ReflectionTestUtils.setField(scanService, "scannerPassword", "enicilion2026");
        
        when(checkinRepository.save(any(CheckinEvent.class))).thenAnswer(invocation -> {
            CheckinEvent event = invocation.getArgument(0);
            // Simulate database auditing populating createdAt
            org.springframework.test.util.ReflectionTestUtils.setField(event, "createdAt", OffsetDateTime.now());
            return event;
        });
    }

    @Test
    void testScanValidTicket() {
        String code = "T1";
        
        User user = new User();
        user.setFullName("John Doe");
        user.setEmail("john@example.com");

        SpectatorTicket ticket = new SpectatorTicket();
        ticket.setTicketCode(code);
        ticket.setStatus(TicketStatus.paid);
        ticket.setUser(user);

        when(ticketRepository.findByTicketCodeForUpdate(code)).thenReturn(Optional.of(ticket));
        when(checkinRepository.countByTicketCodeAndAction(code, "scan_attempt")).thenReturn(0);
        
        // Mock save returning the same object
        when(ticketRepository.save(any(SpectatorTicket.class))).thenReturn(ticket);

        // Run scan
        ScanResponse response = scanService.scanTicket(code, "enicilion2026");

        assertTrue(response.isValid());
        assertEquals("John Doe", response.getName());
        assertEquals(TicketStatus.checked_in, ticket.getStatus());
        
        verify(ticketRepository).save(ticket);
        verify(liveCheckinService).publishCheckin(any(CheckinEvent.class), eq(ticket), anyInt());
        verify(firebaseSyncService).syncCheckInState(eq(code), any(OffsetDateTime.class), anyInt());
    }

    @Test
    void testScanDuplicateTicket() {
        String code = "T1";
        
        User user = new User();
        user.setFullName("John Doe");
        user.setEmail("john@example.com");

        SpectatorTicket ticket = new SpectatorTicket();
        ticket.setTicketCode(code);
        ticket.setStatus(TicketStatus.checked_in);
        ticket.setUser(user);

        when(ticketRepository.findByTicketCodeForUpdate(code)).thenReturn(Optional.of(ticket));
        when(checkinRepository.countByTicketCodeAndAction(code, "scan_attempt")).thenReturn(0);

        // Run duplicate scan
        ScanResponse response = scanService.scanTicket(code, "enicilion2026");

        assertFalse(response.isValid());
        assertEquals("⚠️ Already scanned!", response.getMessage());
        assertEquals("John Doe", response.getName());
        
        verify(liveCheckinService).publishCheckin(any(CheckinEvent.class), eq(ticket), anyInt());
    }
}

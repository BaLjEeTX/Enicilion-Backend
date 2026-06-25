package com.enicilion.backend.tickets.service;

import com.enicilion.backend.auth.entity.User;
import com.enicilion.backend.tickets.dto.CheckinFeedEventDto;
import com.enicilion.backend.tickets.entity.CheckinEvent;
import com.enicilion.backend.tickets.entity.SpectatorTicket;
import com.enicilion.backend.tickets.entity.TicketStatus;
import com.enicilion.backend.tickets.repository.CheckinRepository;
import com.enicilion.backend.tickets.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LiveCheckinServiceTest {

    @Mock
    private CheckinRepository checkinRepository;

    @Mock
    private TicketRepository ticketRepository;

    @InjectMocks
    private LiveCheckinService liveCheckinService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testSubscribe() {
        SseEmitter emitter = liveCheckinService.subscribe();
        assertNotNull(emitter);
    }

    @Test
    void testGetRecentCheckinsEmpty() {
        when(checkinRepository.findTop50ByOrderByCreatedAtDesc()).thenReturn(Collections.emptyList());
        List<CheckinFeedEventDto> result = liveCheckinService.getRecentCheckins();
        assertTrue(result.isEmpty());
        verify(ticketRepository, never()).findByTicketCodeIn(anySet());
    }

    @Test
    void testGetRecentCheckinsWithData() {
        CheckinEvent event = CheckinEvent.builder()
                .ticketCode("T1")
                .action("scan_success")
                .gate("gate_api")
                .reason("ticket validation success")
                .createdAt(OffsetDateTime.now())
                .build();
        when(checkinRepository.findTop50ByOrderByCreatedAtDesc()).thenReturn(List.of(event));

        User user = new User();
        user.setFullName("John Doe");
        user.setEmail("john@example.com");

        SpectatorTicket ticket = new SpectatorTicket();
        ticket.setTicketCode("T1");
        ticket.setUser(user);

        when(ticketRepository.findByTicketCodeIn(anySet())).thenReturn(List.of(ticket));
        when(ticketRepository.countByStatusIn(anyList())).thenReturn(10);

        List<CheckinFeedEventDto> result = liveCheckinService.getRecentCheckins();

        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
        CheckinFeedEventDto dto = result.get(0);
        assertEquals("T1", dto.getTicketCode());
        assertEquals("John Doe", dto.getBuyerName());
        assertEquals("john@example.com", dto.getBuyerEmail());
        assertEquals(10, dto.getTotalCheckedIn());
    }
}

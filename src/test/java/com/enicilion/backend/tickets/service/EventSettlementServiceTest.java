package com.enicilion.backend.tickets.service;

import com.enicilion.backend.common.exception.BadValidationException;
import com.enicilion.backend.common.exception.ResourceNotFoundException;
import com.enicilion.backend.influencer.repository.InfluencerEarningsLedgerRepository;
import com.enicilion.backend.tickets.entity.*;
import com.enicilion.backend.tickets.repository.EventRepository;
import com.enicilion.backend.tickets.repository.EventSummaryRepository;
import com.enicilion.backend.tickets.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EventSettlementServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private EventSummaryRepository eventSummaryRepository;

    @Mock
    private InfluencerEarningsLedgerRepository influencerEarningsLedgerRepository;

    @InjectMocks
    private EventSettlementService eventSettlementService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testSettleEvent_Success() {
        UUID eventId = UUID.randomUUID();
        Event event = new Event();
        event.setId(eventId);
        event.setStatus(EventStatus.completed);

        TicketTier tier = new TicketTier();
        tier.setPrice(BigDecimal.valueOf(1000));

        SpectatorTicket ticket1 = new SpectatorTicket();
        ticket1.setTier(tier);
        ticket1.setDiscountApplied(100);

        SpectatorTicket ticket2 = new SpectatorTicket();
        ticket2.setTier(tier);
        ticket2.setDiscountApplied(0);

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(eventSummaryRepository.findByEventId(eventId)).thenReturn(Optional.empty());
        when(ticketRepository.findByEventIdAndStatusIn(eventId, List.of(TicketStatus.paid, TicketStatus.checked_in))).thenReturn(List.of(ticket1, ticket2));
        when(influencerEarningsLedgerRepository.sumAmountByEventId(eventId)).thenReturn(BigDecimal.valueOf(50));
        when(ticketRepository.countByEventIdAndStatus(eventId, TicketStatus.checked_in)).thenReturn(1);
        when(eventSummaryRepository.save(any(EventSummary.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EventSummary summary = eventSettlementService.settleEvent(eventId);

        assertNotNull(summary);
        assertEquals(event, summary.getEvent());
        assertEquals(0, BigDecimal.valueOf(2000).compareTo(summary.getGrossRevenue())); // 1000 + 1000
        assertEquals(0, BigDecimal.valueOf(1900).compareTo(summary.getNetRevenue())); // 2000 - 100
        assertEquals(0, BigDecimal.valueOf(98).compareTo(summary.getPlatformFee())); // 2 tickets * 49
        assertEquals(0, BigDecimal.valueOf(50).compareTo(summary.getCommission()));
        assertEquals(2, summary.getTicketsSold());
        assertEquals(1, summary.getTicketsCheckedIn());

        verify(eventSummaryRepository, times(1)).save(any(EventSummary.class));
    }

    @Test
    void testSettleEvent_NotCompleted() {
        UUID eventId = UUID.randomUUID();
        Event event = new Event();
        event.setId(eventId);
        event.setStatus(EventStatus.ongoing);

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        assertThrows(BadValidationException.class, () -> eventSettlementService.settleEvent(eventId));
        verify(eventSummaryRepository, never()).save(any());
    }

    @Test
    void testSettleEvent_AlreadySettled() {
        UUID eventId = UUID.randomUUID();
        Event event = new Event();
        event.setId(eventId);
        event.setStatus(EventStatus.completed);

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(eventSummaryRepository.findByEventId(eventId)).thenReturn(Optional.of(new EventSummary()));

        assertThrows(BadValidationException.class, () -> eventSettlementService.settleEvent(eventId));
        verify(eventSummaryRepository, never()).save(any());
    }

    @Test
    void testSettleEvent_EventNotFound() {
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> eventSettlementService.settleEvent(eventId));
    }
}

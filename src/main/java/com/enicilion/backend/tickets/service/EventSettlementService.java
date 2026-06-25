package com.enicilion.backend.tickets.service;

import com.enicilion.backend.common.exception.BadValidationException;
import com.enicilion.backend.common.exception.ResourceNotFoundException;
import com.enicilion.backend.influencer.repository.InfluencerEarningsLedgerRepository;
import com.enicilion.backend.tickets.entity.*;
import com.enicilion.backend.tickets.repository.EventRepository;
import com.enicilion.backend.tickets.repository.EventSummaryRepository;
import com.enicilion.backend.tickets.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventSettlementService {

    private final EventRepository eventRepository;
    private final TicketRepository ticketRepository;
    private final EventSummaryRepository eventSummaryRepository;
    private final InfluencerEarningsLedgerRepository influencerEarningsLedgerRepository;

    /**
     * Settles the event financial data and archives it.
     * Throws an exception if the event is not completed.
     *
     * @param eventId the event ID to settle
     * @return the saved EventSummary
     */
    @Transactional
    public EventSummary settleEvent(UUID eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with ID: " + eventId));

        if (event.getStatus() != EventStatus.completed) {
            throw new BadValidationException("Event must be COMPLETED before settling financials. Current status: " + event.getStatus());
        }

        // Check if already settled
        if (eventSummaryRepository.findByEventId(eventId).isPresent()) {
            throw new BadValidationException("Financials for this event have already been settled and archived.");
        }

        // 1. Retrieve all paid and checked-in tickets
        List<SpectatorTicket> paidTickets = ticketRepository.findByEventIdAndStatusIn(eventId, List.of(TicketStatus.paid, TicketStatus.checked_in));

        BigDecimal grossRevenue = BigDecimal.ZERO;
        BigDecimal totalDiscounts = BigDecimal.ZERO;

        for (SpectatorTicket ticket : paidTickets) {
            BigDecimal price = (ticket.getTier() != null && ticket.getTier().getPrice() != null) 
                    ? ticket.getTier().getPrice() 
                    : BigDecimal.ZERO;
            grossRevenue = grossRevenue.add(price);
            totalDiscounts = totalDiscounts.add(BigDecimal.valueOf(ticket.getDiscountApplied()));
        }

        // 2. Net Revenue = Gross - discounts
        BigDecimal netRevenue = grossRevenue.subtract(totalDiscounts);

        // 3. Platform Fee = 49 INR per ticket sold
        BigDecimal platformFee = BigDecimal.valueOf(49.00).multiply(BigDecimal.valueOf(paidTickets.size()));

        // 4. Commission = sum from ledger
        BigDecimal commission = influencerEarningsLedgerRepository.sumAmountByEventId(eventId);
        if (commission == null) {
            commission = BigDecimal.ZERO;
        }

        // 5. Check-in count
        int ticketsCheckedIn = ticketRepository.countByEventIdAndStatus(eventId, TicketStatus.checked_in);

        EventSummary summary = EventSummary.builder()
                .event(event)
                .grossRevenue(grossRevenue)
                .netRevenue(netRevenue)
                .platformFee(platformFee)
                .commission(commission)
                .ticketsSold(paidTickets.size())
                .ticketsCheckedIn(ticketsCheckedIn)
                .settledAt(OffsetDateTime.now())
                .build();

        EventSummary savedSummary = eventSummaryRepository.save(summary);
        log.info("Successfully settled and archived financials for event {}: gross = {}, net = {}, platform = {}, commission = {}", 
                eventId, grossRevenue, netRevenue, platformFee, commission);

        return savedSummary;
    }

    /**
     * Gets the settlement summary for an event, if it exists.
     */
    public EventSummary getSettlementSummary(UUID eventId) {
        return eventSummaryRepository.findByEventId(eventId)
                .orElse(null);
    }
}

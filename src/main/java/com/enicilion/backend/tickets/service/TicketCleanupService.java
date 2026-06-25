package com.enicilion.backend.tickets.service;

import com.enicilion.backend.tickets.entity.SpectatorTicket;
import com.enicilion.backend.tickets.entity.TicketStatus;
import com.enicilion.backend.tickets.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketCleanupService {

    private final TicketRepository ticketRepository;
    private final RedisInventoryService redisInventoryService;

    /**
     * Finds and cancels booked spectator tickets that have not been paid
     * within the 15-minute checkout window, returning their inventory to Redis.
     */
    @Scheduled(fixedRate = 60000) // Runs every minute
    @SchedulerLock(name = "cleanupExpiredBookingsLock", lockAtMostFor = "50s", lockAtLeastFor = "10s")
    @Transactional
    public void cleanupExpiredBookings() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusMinutes(15);
        List<SpectatorTicket> expiredTickets = ticketRepository.findByStatusAndBookedAtBefore(
                TicketStatus.booked,
                cutoff
        );

        if (expiredTickets.isEmpty()) {
            return;
        }

        log.info("Found {} expired ticket bookings to clean up", expiredTickets.size());

        OffsetDateTime now = OffsetDateTime.now();
        for (SpectatorTicket ticket : expiredTickets) {
            ticket.setStatus(TicketStatus.cancelled);
            ticket.setUpdatedAt(now);

            if (ticket.getTier() != null) {
                redisInventoryService.releaseInventory(ticket.getTier().getId(), 1);
                log.info("Released inventory to Redis for expired ticket code: {}, tier: {}", 
                        ticket.getTicketCode(), ticket.getTier().getId());
            }
        }
        ticketRepository.saveAll(expiredTickets);
    }
}

package com.enicilion.backend.tickets.repository;

import com.enicilion.backend.tickets.entity.SpectatorTicket;
import com.enicilion.backend.tickets.entity.TicketStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.query.Param;

@Repository
public interface TicketRepository extends JpaRepository<SpectatorTicket, UUID> {
    
    Optional<SpectatorTicket> findByTicketCode(String ticketCode);

    @Query("SELECT t FROM SpectatorTicket t LEFT JOIN FETCH t.user LEFT JOIN FETCH t.event LEFT JOIN FETCH t.tier LEFT JOIN FETCH t.payment WHERE t.ticketCode = :ticketCode")
    Optional<SpectatorTicket> findByTicketCodeWithDetails(@Param("ticketCode") String ticketCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM SpectatorTicket t WHERE t.ticketCode = :ticketCode")
    Optional<SpectatorTicket> findByTicketCodeForUpdate(String ticketCode);

    int countByTierIdAndStatusIn(UUID tierId, Collection<TicketStatus> statuses);
    
    int countByTierIdAndStatusAndBookedAtAfter(UUID tierId, TicketStatus status, OffsetDateTime date);
    
    int countByStatusIn(Collection<TicketStatus> statuses);
    
    int countByStatusInAndBookedAtAfter(Collection<TicketStatus> statuses, OffsetDateTime date);
    
    List<SpectatorTicket> findByUserId(UUID userId);

    List<SpectatorTicket> findByPaymentId(UUID paymentId);

    @Query("SELECT t FROM SpectatorTicket t LEFT JOIN FETCH t.user LEFT JOIN FETCH t.tier LEFT JOIN FETCH t.event LEFT JOIN FETCH t.payment WHERE t.ticketCode IN :ticketCodes")
    List<SpectatorTicket> findByTicketCodeIn(@Param("ticketCodes") Collection<String> ticketCodes);

    List<SpectatorTicket> findByBookedAtBetweenOrderByBookedAtDesc(OffsetDateTime start, OffsetDateTime end);

    @Query("SELECT t FROM SpectatorTicket t LEFT JOIN FETCH t.tier LEFT JOIN FETCH t.user LEFT JOIN FETCH t.payment WHERE t.bookedAt >= :start AND t.bookedAt <= :end ORDER BY t.bookedAt DESC")
    java.util.stream.Stream<SpectatorTicket> streamByBookedAtBetween(@Param("start") OffsetDateTime start, @Param("end") OffsetDateTime end);

    List<SpectatorTicket> findByStatusAndBookedAtBefore(TicketStatus status, OffsetDateTime cutoff);

    List<SpectatorTicket> findByEventIdAndStatus(UUID eventId, TicketStatus status);
    List<SpectatorTicket> findByEventIdAndStatusIn(UUID eventId, Collection<TicketStatus> statuses);
    int countByEventIdAndStatus(UUID eventId, TicketStatus status);
    int countByEventIdAndStatusAndCheckedInAtIsNotNull(UUID eventId, TicketStatus status);
}

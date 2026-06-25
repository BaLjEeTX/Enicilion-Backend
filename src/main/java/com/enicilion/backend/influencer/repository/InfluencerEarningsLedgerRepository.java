package com.enicilion.backend.influencer.repository;

import com.enicilion.backend.influencer.entity.InfluencerEarningsLedger;
import com.enicilion.backend.influencer.entity.LedgerStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InfluencerEarningsLedgerRepository extends JpaRepository<InfluencerEarningsLedger, UUID> {
    List<InfluencerEarningsLedger> findByInfluencerProfileId(UUID profileId);
    List<InfluencerEarningsLedger> findByInfluencerProfileIdAndStatus(UUID profileId, LedgerStatus status);
    List<InfluencerEarningsLedger> findByPaymentId(UUID paymentId);
    List<InfluencerEarningsLedger> findAllByOrderByCreatedAtDesc();

    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(SUM(l.amount), 0) FROM InfluencerEarningsLedger l WHERE l.ticket.event.id = :eventId")
    java.math.BigDecimal sumAmountByEventId(UUID eventId);
}

package com.enicilion.backend.influencer.entity;

import com.enicilion.backend.common.base.BaseEntity;
import com.enicilion.backend.payments.entity.Payment;
import com.enicilion.backend.tickets.entity.SpectatorTicket;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "influencer_earnings_ledger", indexes = {
    @Index(name = "idx_ledger_profile_id", columnList = "influencer_profile_id"),
    @Index(name = "idx_ledger_payment_id", columnList = "payment_id"),
    @Index(name = "idx_ledger_ticket_id", columnList = "ticket_id"),
    @Index(name = "idx_ledger_status", columnList = "status"),
    @Index(name = "idx_ledger_payout_id", columnList = "payout_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class InfluencerEarningsLedger extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "UUID")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "influencer_profile_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private InfluencerProfile influencerProfile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private SpectatorTicket ticket;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private LedgerStatus status = LedgerStatus.pending;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payout_id")
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private InfluencerPayout payout;
}

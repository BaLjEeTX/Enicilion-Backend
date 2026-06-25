package com.enicilion.backend.tickets.entity;

import com.enicilion.backend.auth.entity.User;
import com.enicilion.backend.payments.entity.Payment;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "spectator_tickets", indexes = {
    @Index(name = "idx_spectator_tickets_event_id", columnList = "event_id"),
    @Index(name = "idx_spectator_tickets_user_id", columnList = "user_id"),
    @Index(name = "idx_spectator_tickets_payment_id", columnList = "payment_id"),
    @Index(name = "idx_spectator_tickets_status", columnList = "status"),
    @Index(name = "idx_spectator_tickets_ticket_code", columnList = "ticket_code")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpectatorTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "UUID")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tier_id")
    private TicketTier tier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @Column(name = "ticket_code", nullable = false, unique = true, length = 64)
    private String ticketCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TicketStatus status = TicketStatus.booked;

    @CreatedDate
    @Column(name = "booked_at", nullable = false, updatable = false)
    private OffsetDateTime bookedAt;

    @Column(name = "checked_in_at")
    private OffsetDateTime checkedInAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "apple_pass_id")
    private String applePassId;

    @Column(name = "google_wallet_object_id")
    private String googleWalletObjectId;

    @Column(name = "wallet_last_updated")
    private OffsetDateTime walletLastUpdated;

    @Column(name = "discount_applied", nullable = false)
    @Builder.Default
    private int discountApplied = 0;

    @Column(name = "referral_code_used", length = 20)
    private String referralCodeUsed;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", name = "registration_responses")
    private String registrationResponses;
}

package com.enicilion.backend.tickets.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "event_summaries", indexes = {
    @Index(name = "idx_event_summaries_event_id", columnList = "event_id")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "UUID")
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false, unique = true)
    private Event event;

    @Column(name = "gross_revenue", nullable = false, precision = 12, scale = 2)
    private BigDecimal grossRevenue;

    @Column(name = "net_revenue", nullable = false, precision = 12, scale = 2)
    private BigDecimal netRevenue;

    @Column(name = "platform_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal platformFee;

    @Column(name = "commission", nullable = false, precision = 12, scale = 2)
    private BigDecimal commission;

    @Column(name = "tickets_sold", nullable = false)
    private Integer ticketsSold;

    @Column(name = "tickets_checked_in", nullable = false)
    private Integer ticketsCheckedIn;

    @CreatedDate
    @Column(name = "settled_at", nullable = false, updatable = false)
    private OffsetDateTime settledAt;
}

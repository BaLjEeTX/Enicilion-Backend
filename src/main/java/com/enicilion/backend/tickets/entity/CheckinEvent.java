package com.enicilion.backend.tickets.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "checkin_events", indexes = {
    @Index(name = "idx_checkin_events_ticket_code", columnList = "ticket_code")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckinEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "UUID")
    private UUID id;

    @Column(name = "ticket_id", columnDefinition = "UUID")
    private UUID ticketId;

    @Column(name = "ticket_code", nullable = false, length = 64)
    private String ticketCode;

    @Column(nullable = false, length = 30)
    private String action; // e.g. "scan_attempt", "manual_checkin", "gate"

    @Column(length = 50)
    private String gate;

    @Column(name = "operator_id", columnDefinition = "TEXT")
    private String operatorId;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}

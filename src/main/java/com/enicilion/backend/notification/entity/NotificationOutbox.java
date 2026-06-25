package com.enicilion.backend.notification.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "notification_outbox", indexes = {
    @Index(name = "idx_notification_outbox_status", columnList = "status"),
    @Index(name = "idx_notification_outbox_scheduled_at", columnList = "scheduled_at")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "UUID")
    private UUID id;

    @Column(name = "ticket_code", nullable = false, length = 64)
    private String ticketCode;

    @Column(name = "override_email")
    private String overrideEmail;

    @Column(name = "override_phone", length = 50)
    private String overridePhone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(nullable = false)
    @Builder.Default
    private int attempts = 0;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "scheduled_at", nullable = false)
    private OffsetDateTime scheduledAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}

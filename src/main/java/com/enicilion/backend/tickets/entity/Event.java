package com.enicilion.backend.tickets.entity;

import com.enicilion.backend.auth.entity.User;
import com.enicilion.backend.common.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "events", indexes = {
    @Index(name = "idx_events_slug", columnList = "slug"),
    @Index(name = "idx_events_status", columnList = "status"),
    @Index(name = "idx_events_event_date", columnList = "event_date"),
    @Index(name = "idx_events_organizer_id", columnList = "organizer_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Event extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "UUID")
    private UUID id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, unique = true, length = 100)
    private String slug;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 255)
    private String location;

    @Column(name = "event_date", nullable = false)
    private OffsetDateTime eventDate;

    @Column(name = "applications_open_at")
    private OffsetDateTime applicationsOpenAt;

    @Column(name = "applications_close_at")
    private OffsetDateTime applicationsCloseAt;

    @Column(name = "tickets_open_at")
    private OffsetDateTime ticketsOpenAt;

    @Column(name = "max_drifters")
    private Integer maxDrifters;

    @Column(name = "max_spectators")
    private Integer maxSpectators;

    @Column(name = "drifter_fee", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal drifterFee = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private EventStatus status = EventStatus.draft;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private User creator;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizer_id")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private com.enicilion.backend.organizers.entity.Organizer organizer;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", name = "registration_schema")
    private String registrationSchema;
}

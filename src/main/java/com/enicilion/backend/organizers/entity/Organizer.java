package com.enicilion.backend.organizers.entity;

import com.enicilion.backend.common.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "organizers", indexes = {
    @Index(name = "idx_organizers_email", columnList = "email")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Organizer extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "UUID")
    private UUID id;

    @Column(name = "business_name", nullable = false, length = 255)
    private String businessName;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "logo_url", length = 512)
    private String logoUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", name = "payout_settings")
    private String payoutSettings; // JSON data for payout preferences
}

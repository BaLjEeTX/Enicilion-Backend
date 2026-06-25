# ENICILION Enterprise Java Spring Boot Migration Specifications

This document serves as the absolute, 100% complete, and self-contained development blueprint for migrating the Enicilion platform backend from Node.js (TypeScript/Express) to Java 21 and Spring Boot 3.x. Every entity, route, business logic flow, database table schema, and integration detail is defined with precision to ensure zero ambiguity for developers or AI agents.

---

## 1. System Architecture & Tech Stack

The migrated backend must follow a **Modular Monolith** architecture, using standard Spring patterns. It is organized into clean domain modules to prevent dependency circles.

- **Language Runtime**: Java 21 (LTS)
- **Core Framework**: Spring Boot 3.3.x, Spring MVC, Spring Data JPA (Hibernate 6.x)
- **Security**: Spring Security 6.x, JJWT (Java JWT Library), BCrypt (12 rounds)
- **Real-Time Sync**: Firebase Admin Java SDK 9.x (syncs ticket check-in and transaction states to live Firestore console)
- **Communications**: 
  - Transactional Emails: HTTP Integration with ZeptoMail API (sending e-ticket PDFs as attachments)
  - WhatsApp Notifications: HTTP Integration with AiSensy API (sending confirmation details)
- **Caching & Rate Limiting**: Redis (using Spring Data Redis and Lettuce connector), Bucket4j for endpoint-level rate limits
- **Object Mapping**: MapStruct 1.5.x for DTO-to-Entity conversions
- **PDF Generation**: OpenPDF 2.0.x (translating custom coordinates and canvas drawings from pdfkit)
- **Database**: PostgreSQL (matching existing columns, indexes, and custom enums)
- **Image Processing**: TwelveMonkeys ImageIO (enables WebP support) or Thumbnailator (resizing/compressing drifter car photos)

---

## 2. Core Database Schemas (JPA Entities)

This section maps the live PostgreSQL tables to Java JPA entities. Custom PostgreSQL enums are mapped using Java `@Enumerated(EnumType.STRING)`.

### 2.1. Enumerated Custom Types
Create Java enums corresponding to the database enum types:
```java
public enum UserRole { admin, staff, drifter, spectator, user }
public enum EventStatus { draft, published, ongoing, completed, cancelled }
public enum ApplicationStatus { pending, approved, rejected, waitlisted, withdrawn }
public enum CarStatus { active, inactive }
public enum TicketStatus { booked, paid, checked_in, cancelled }
public enum PhotoStatus { pending, approved, rejected }
public enum PaymentProvider { stripe, paypal, razorpay, payu, cashfree, manual }
public enum PaymentStatus { pending, paid, failed, refunded }
public enum ReferenceType { drifter_application, spectator_ticket, payment_intent }
public enum ScheduleCategory { session, break, ceremony, practice, qualifying, final }
public enum FeedbackCategory { organization, safety, experience, other }
```

### 2.2. Core JPA Models

#### 1. User Entity (`users` table)
```java
package com.enicilion.backend.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_users_email", columnList = "email"),
    @Index(name = "idx_users_role", columnList = "role")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "UUID")
    private String id;

    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(unique = true, length = 30)
    private String whatsapp;

    @Column(length = 100)
    private String instagram;

    @Column(length = 100)
    private String city;

    @Column(name = "password_hash", nullable = false, columnDefinition = "TEXT")
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private UserRole role = UserRole.user;

    @Column(name = "is_banned", nullable = false)
    @Builder.Default
    private boolean isBanned = false;

    @Column(name = "referral_code", unique = true, length = 20)
    private String referralCode;

    @Column(name = "referred_by", length = 20)
    private String referredBy;

    @Column(name = "email_bounced", nullable = false)
    @Builder.Default
    private boolean emailBounced = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}
```

#### 2. Event Entity (`events` table)
```java
package com.enicilion.backend.tickets.entity;

import com.enicilion.backend.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "events", indexes = {
    @Index(name = "idx_events_slug", columnList = "slug"),
    @Index(name = "idx_events_status", columnList = "status"),
    @Index(name = "idx_events_event_date", columnList = "event_date")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Event {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "UUID")
    private String id;

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
    private User creator;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}
```

#### 3. TicketTier Entity (`ticket_tiers` table)
```java
package com.enicilion.backend.tickets.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "ticket_tiers", indexes = {
    @Index(name = "idx_ticket_tiers_is_public", columnList = "is_public")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketTier {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "UUID")
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    private Integer quantity;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_public", nullable = false)
    @Builder.Default
    private boolean isPublic = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
```

#### 4. SpectatorTicket Entity (`spectator_tickets` table)
```java
package com.enicilion.backend.tickets.entity;

import com.enicilion.backend.auth.entity.User;
import com.enicilion.backend.payments.entity.Payment;
import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "spectator_tickets", indexes = {
    @Index(name = "idx_spectator_tickets_event_id", columnList = "event_id"),
    @Index(name = "idx_spectator_tickets_user_id", columnList = "user_id"),
    @Index(name = "idx_spectator_tickets_payment_id", columnList = "payment_id"),
    @Index(name = "idx_spectator_tickets_status", columnList = "status"),
    @Index(name = "idx_spectator_tickets_ticket_code", columnList = "ticket_code")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpectatorTicket {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "UUID")
    private String id;

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

    @Column(name = "booked_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime bookedAt = OffsetDateTime.now();

    @Column(name = "checked_in_at")
    private OffsetDateTime checkedInAt;

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Column(name = "discount_applied", nullable = false)
    @Builder.Default
    private int discountApplied = 0;

    @Column(name = "referral_code_used", length = 20)
    private String referralCodeUsed;
}
```

#### 5. Payment Entity (`payments` table)
```java
package com.enicilion.backend.payments.entity;

import com.enicilion.backend.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "payments", indexes = {
    @Index(name = "idx_payments_user_id", columnList = "user_id"),
    @Index(name = "idx_payments_status", columnList = "status"),
    @Index(name = "idx_payments_idempotency_key", columnList = "idempotency_key"),
    @Index(name = "idx_payments_provider_tx_id", columnList = "provider_tx_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "UUID")
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "reference_id", columnDefinition = "UUID")
    private String referenceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type")
    private ReferenceType referenceType;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentProvider provider;

    @Column(name = "provider_tx_id", length = 255)
    private String providerTxId;

    @Column(name = "provider_session", length = 255)
    private String providerSession;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.pending;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 255)
    private String idempotencyKey;

    @Column(columnDefinition = "jsonb")
    private String metadata; // Serialized JSON mapped via Jackson converter

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    @Column(name = "refunded_at")
    private OffsetDateTime refundedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}
```

#### 6. Coupon Entity (`coupons` table)
```java
package com.enicilion.backend.coupons.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "coupons", indexes = {
    @Index(name = "idx_coupons_is_active", columnList = "is_active"),
    @Index(name = "idx_coupons_reserved_payment_id", columnList = "reserved_payment_id"),
    @Index(name = "idx_coupons_used_payment_id", columnList = "used_payment_id"),
    @Index(name = "idx_coupons_reserved_at", columnList = "reserved_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Coupon {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "UUID")
    private String id;

    @Column(nullable = false, unique = true, length = 100)
    private String code;

    @Column(name = "max_uses", nullable = false)
    @Builder.Default
    private int maxUses = 1;

    @Column(name = "used_count", nullable = false)
    @Builder.Default
    private int usedCount = 0;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "reserved_payment_id", columnDefinition = "UUID")
    private String reservedPaymentId;

    @Column(name = "reserved_at")
    private OffsetDateTime reservedAt;

    @Column(name = "used_payment_id", columnDefinition = "UUID")
    private String usedPaymentId;

    @Column(name = "used_at")
    private OffsetDateTime usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}
```

#### 7. Car Entity (`cars` table)
```java
package com.enicilion.backend.applications.entity;

import com.enicilion.backend.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "cars", indexes = {
    @Index(name = "idx_cars_owner_id", columnList = "owner_id"),
    @Index(name = "idx_cars_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Car {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "UUID")
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false, length = 100)
    private String make;

    @Column(nullable = false, length = 100)
    private String model;

    @Column(nullable = false)
    private short year;

    @Column(length = 50)
    private String color;

    @Column(name = "license_plate", unique = true, length = 30)
    private String licensePlate;

    @Column(unique = true, length = 17)
    private String vin;

    @Column(name = "engine_spec", length = 255)
    private String engineSpec;

    @Column(columnDefinition = "TEXT")
    private String modifications;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private CarStatus status = CarStatus.active;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}
```

#### 8. DrifterApplication Entity (`drifter_applications` table)
```java
package com.enicilion.backend.applications.entity;

import com.enicilion.backend.auth.entity.User;
import com.enicilion.backend.tickets.entity.Event;
import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "drifter_applications", uniqueConstraints = {
    @UniqueConstraint(name = "uq_drifter_application_event_user", columnNames = {"event_id", "user_id"})
}, indexes = {
    @Index(name = "idx_drifter_apps_event", columnList = "event_id"),
    @Index(name = "idx_drifter_apps_user", columnList = "user_id"),
    @Index(name = "idx_drifter_apps_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DrifterApplication {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "UUID")
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ApplicationStatus status = ApplicationStatus.pending;

    @Column(name = "waitlist_position")
    private Integer waitlistPosition;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewer;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "admin_notes", columnDefinition = "TEXT")
    private String adminNotes;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime submittedAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}
```

#### 9. ApplicationCar Entity (`application_cars` table)
```java
package com.enicilion.backend.applications.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "application_cars", uniqueConstraints = {
    @UniqueConstraint(name = "uq_application_car", columnNames = {"application_id", "car_id"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationCar {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "UUID")
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    private DrifterApplication application;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "car_id", nullable = false)
    private Car car;

    @Column(name = "engine_spec_override", length = 255)
    private String engineSpecOverride;

    @Column(name = "modifications_override", columnDefinition = "TEXT")
    private String modificationsOverride;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private short sortOrder = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
```

#### 10. ApplicationPhoto Entity (`application_photos` table)
```java
package com.enicilion.backend.applications.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "application_photos")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationPhoto {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "UUID")
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    private DrifterApplication application;

    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    @Column(name = "storage_path", nullable = false, columnDefinition = "TEXT")
    private String storagePath;

    private Integer width;
    private Integer height;

    @Column(name = "size_bytes")
    private Integer sizeBytes;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PhotoStatus status = PhotoStatus.pending;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private short sortOrder = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
```

#### 11. SupportTicket Entity (`support_tickets` table)
```java
package com.enicilion.backend.support.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "support_tickets", indexes = {
    @Index(name = "idx_support_tickets_user_id", columnList = "user_id"),
    @Index(name = "idx_support_tickets_created_at", columnList = "created_at DESC")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupportTicket {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "UUID")
    private String id;

    @Column(name = "user_id", columnDefinition = "UUID")
    private String userId;

    @Column(nullable = false, length = 100)
    private String category;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "contact_name", length = 255)
    private String contactName;

    @Column(name = "contact_phone", length = 30)
    private String contactPhone;

    @Column(name = "contact_email", length = 255)
    private String contactEmail;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String status = "open";

    @Column(nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private String metadata = "{}";

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}
```

#### 12. CheckinEvent Entity (`checkin_events` table)
```java
package com.enicilion.backend.tickets.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "checkin_events", indexes = {
    @Index(name = "idx_checkin_events_ticket_code", columnList = "ticket_code")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckinEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "UUID")
    private String id;

    @Column(name = "ticket_id", columnDefinition = "UUID")
    private String ticketId;

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

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
```

### 2.3. Operational Schema Inventory (Spring JDBC / Optional JPA mapping)
The following tables are used by administrative tools, runsheets, and physical gate services. They should be created via Flyway or Hibernate configuration:

- **`pos_sales`**: POS terminal tickets issued cash/offline.
  - Columns: `id` UUID, `tier_id` UUID, `buyer_name` TEXT, `buyer_phone` VARCHAR(30), `buyer_email` VARCHAR(255), `amount` NUMERIC(10,2), `pay_method` VARCHAR(30), `ticket_code` VARCHAR(64) UNIQUE, `issued_by` TEXT, `issued_at` TIMESTAMPTZ, `voided_at` TIMESTAMPTZ, `void_reason` TEXT, `reprint_count` INT, `last_reprinted_at` TIMESTAMPTZ.
- **`ticket_notes`**: Administrative comments on ticket issues.
  - Columns: `id` UUID, `ticket_id` UUID, `ticket_code` VARCHAR(64), `note` TEXT, `created_by` TEXT, `created_at` TIMESTAMPTZ.
- **`ticket_transfers`**: Records logs of ticket owner changes.
  - Columns: `id` UUID, `ticket_id` UUID, `from_user_id` UUID, `to_user_id` UUID, `reason` TEXT, `created_by` TEXT, `created_at` TIMESTAMPTZ.
- **`audit_logs`**: Action logging tracking modifying requests.
  - Columns: `id` UUID, `admin_hash` TEXT, `action` TEXT, `payload` JSONB, `sensitive` BOOLEAN, `status_code` INT, `ip` TEXT, `created_at` TIMESTAMPTZ.
- **`event_config_store`**: A singleton table holding current active parameters.
  - Columns: `id` INT PRIMARY KEY (constrain to `id = 1`), `name` VARCHAR(255), `event_date` TIMESTAMPTZ, `venue` VARCHAR(255), `capacity` INT, `metadata` JSONB, `updated_at` TIMESTAMPTZ.
- **`referral_codes_ext`**: Extends validation properties of coupons/affiliates.
  - Columns: `id` UUID, `name` VARCHAR(255), `code` VARCHAR(30) UNIQUE, `discount` INT, `discount_percent` INT, `max_uses` INT, `used_count` INT, `is_active` BOOLEAN, `reserved_payment_id` UUID, `reserved_at` TIMESTAMPTZ, `created_at` TIMESTAMPTZ.
- **`failed_wa_queue`**: Spooler queue tracking retries of failed AiSensy messages.
  - Columns: `id` UUID, `phone` VARCHAR(30), `message` TEXT, `reason` TEXT, `attempts` INT, `next_retry_at` TIMESTAMPTZ, `status` VARCHAR(30), `created_at` TIMESTAMPTZ, `updated_at` TIMESTAMPTZ.

---

## 3. Core Business Logic Algorithms

### 3.1. Secure Cryptographic Ticket Code Generator
To remediate Vulnerability #1, `Math.random` must be replaced with `java.security.SecureRandom` to prevent guessing:

```java
package com.enicilion.backend.tickets.service;

import java.security.SecureRandom;
import java.util.Base64;

public class TicketCodeGenerator {
    private static final SecureRandom secureRandom = new SecureRandom();
    
    public static String generateSecureCode() {
        byte[] randomBytes = new byte[8];
        secureRandom.nextBytes(randomBytes);
        // Generates safe, readable, non-predictable alphanumeric codes (e.g. MS26-A1B2C3D4E5)
        String hex = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes)
                           .replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        return "MS26-" + hex.substring(0, 8) + "-" + Long.toString(System.currentTimeMillis(), 36).toUpperCase().substring(4);
    }
}
```

### 3.2. Concurrency-Safe Ticket Booking & Checkout
To prevent overselling of spectator ticket inventory, the checkout process must acquire row locks on ticket tiers and coupons inside a serializable transaction block.

```java
@Service
@RequiredArgsConstructor
public class CheckoutService {

    private final TicketTierRepository tierRepository;
    private final SpectatorTicketRepository ticketRepository;
    private final CouponRepository couponRepository;
    private final PaymentRepository paymentRepository;

    @Transactional
    public PaymentResponse checkout(CheckoutRequest request, User user) {
        BigDecimal totalBaseAmount = BigDecimal.ZERO;
        int totalAccessTickets = 0;
        int eligibleCouponTickets = 0;
        BigDecimal eligibleCouponSubtotal = BigDecimal.ZERO;

        for (CartItem item : request.getItems()) {
            // 1. Lock Row using PESSIMISTIC_WRITE (generates SELECT FOR UPDATE)
            TicketTier tier = tierRepository.findByIdForUpdate(item.getTierId())
                .orElseThrow(() -> new ResourceNotFoundException("Tier not found"));

            // 2. Count existing sold counts
            int sold = ticketRepository.countByTierIdAndStatusIn(tier.getId(), List.of(TicketStatus.paid, TicketStatus.checked_in));
            if (tier.getQuantity() != null && (sold + item.getQuantity() > tier.getQuantity())) {
                throw new InventoryExhaustedException("Only " + (tier.getQuantity() - sold) + " tickets left for " + tier.getName());
            }

            BigDecimal overridePrice = getOverridePrice(tier.getName(), tier.getPrice());
            BigDecimal itemSubtotal = overridePrice.multiply(BigDecimal.valueOf(item.getQuantity()));
            
            // Calculate group discount: If purchasing 5+ tickets of the same type, apply 10% discount
            BigDecimal bulkDiscount = BigDecimal.ZERO;
            if (item.getQuantity() >= 5) {
                bulkDiscount = itemSubtotal.multiply(BigDecimal.valueOf(0.10)).setScale(2, RoundingMode.HALF_UP);
                itemSubtotal = itemSubtotal.subtract(bulkDiscount);
            }

            totalBaseAmount = totalBaseAmount.add(itemSubtotal);
            
            if (!isFoodAndBeverage(tier.getName())) {
                totalAccessTickets += item.getQuantity();
            }

            // Exclude already discounted lines from coupon calculations
            boolean isCouponEligible = isEligibleForCoupon(tier.getName()) && (item.getQuantity() < 5);
            if (isCouponEligible) {
                eligibleCouponTickets += item.getQuantity();
                eligibleCouponSubtotal = eligibleCouponSubtotal.add(itemSubtotal);
            }
        }

        // Apply coupon code discount if applicable
        BigDecimal discount = BigDecimal.ZERO;
        String couponCode = normalizeCouponCode(request.getCouponCode());
        if (couponCode != null) {
            Coupon coupon = couponRepository.findByCodeForUpdate(couponCode)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid coupon"));

            if (coupon.getReservedPaymentId() != null) {
                throw new CouponReservationException("Coupon is currently reserved by another pending transaction");
            }
            if (coupon.getUsedPaymentId() != null || coupon.getUsedCount() >= coupon.getMaxUses()) {
                throw new CouponReservationException("Coupon already used");
            }

            // Apply 10% coupon discount
            discount = eligibleCouponSubtotal.multiply(BigDecimal.valueOf(0.10)).setScale(2, RoundingMode.HALF_UP);
        }

        // Service Fee calculation (49 INR per ticket access fee)
        BigDecimal serviceFee = BigDecimal.valueOf(49.00).multiply(BigDecimal.valueOf(totalAccessTickets));
        BigDecimal finalAmount = totalBaseAmount.add(serviceFee).subtract(discount).setScale(0, RoundingMode.HALF_UP);

        // Verify client calculation
        if (request.getClientTotal() != null && request.getClientTotal().compareTo(finalAmount) != 0) {
            throw new BadValidationException("Checkout total mismatch. Please refresh your cart.");
        }

        // 3. Create Payment Intent
        Payment payment = Payment.builder()
            .user(user)
            .amount(finalAmount)
            .provider(PaymentProvider.razorpay)
            .status(PaymentStatus.pending)
            .idempotencyKey("checkout:" + user.getId() + ":" + System.currentTimeMillis())
            .build();
        paymentRepository.save(payment);

        // 4. Reserve Coupon
        if (couponCode != null) {
            couponRepository.reserveCoupon(couponCode, payment.getId(), OffsetDateTime.now());
        }

        // 5. Create Booked Tickets
        for (CartItem item : request.getItems()) {
            for (int i = 0; i < item.getQuantity(); i++) {
                SpectatorTicket ticket = SpectatorTicket.builder()
                    .user(user)
                    .tier(tier)
                    .payment(payment)
                    .ticketCode(TicketCodeGenerator.generateSecureCode())
                    .status(TicketStatus.booked)
                    .discountApplied(isEligibleForCoupon(tier.getName()) ? discount.divide(BigDecimal.valueOf(eligibleCouponTickets)).intValue() : 0)
                    .build();
                ticketRepository.save(ticket);
            }
        }

        return new PaymentResponse(payment.getId(), finalAmount);
    }
}
```

### 3.3. Ticket Gate Scan & Attempts Tracking Algorithm
Gate check-in must track consecutive failed or duplicate scans to identify and block cloned passes. It must write logs and handle syncs correctly:

```java
@Service
@RequiredArgsConstructor
public class ScanService {

    private final SpectatorTicketRepository ticketRepository;
    private final CheckinEventRepository checkinRepository;
    private final FirebaseSyncService firebaseSyncService;
    
    @Value("${app.scanner.password}")
    private String scannerPassword;

    @Transactional
    public ScanResponse scanTicket(String code, String password) {
        // 1. Password Verification
        if (!scannerPassword.equals(password)) {
            throw new UnauthorizedException("Unauthorized: Invalid scanner password");
        }

        // 2. Fetch Ticket
        SpectatorTicket ticket = ticketRepository.findByTicketCode(code)
            .orElse(null);
        if (ticket == null) {
            return new ScanResponse(false, "❌ Invalid ticket", null);
        }

        // 3. Check Cancelled State
        if (ticket.getStatus() == TicketStatus.cancelled) {
            return new ScanResponse(false, "❌ Ticket is blocked/cancelled", null);
        }

        // 4. Record Check-in Attempt
        CheckinEvent attempt = CheckinEvent.builder()
            .ticketId(ticket.getId())
            .ticketCode(ticket.getTicketCode())
            .action("scan_attempt")
            .gate("gate_api")
            .operatorId("scanner_terminal")
            .reason("ticket validation attempt")
            .build();
        checkinRepository.save(attempt);

        // 5. Count Total Attempts
        int attemptsCount = checkinRepository.countByTicketCodeAndAction(code, "scan_attempt");
        if (attemptsCount >= 3) {
            ticket.setStatus(TicketStatus.cancelled);
            ticketRepository.save(ticket);

            // Sync Block state to Firebase Firestore Real-Time console
            firebaseSyncService.syncBlockState(ticket.getTicketCode(), true, attemptsCount);
            return new ScanResponse(false, "❌ Ticket blocked: scanned 3 times", null);
        }

        // 6. Handle Duplicate Scan
        if (ticket.getStatus() == TicketStatus.checked_in) {
            return new ScanResponse(false, "⚠️ Already scanned!", ticket.getUser().getFullName());
        }

        // 7. Validate Paid Status
        if (ticket.getStatus() != TicketStatus.paid) {
            return new ScanResponse(false, "❌ Ticket status: " + ticket.getStatus() + ". Only paid tickets can be scanned.", null);
        }

        // 8. Successful Scan
        ticket.setStatus(TicketStatus.checked_in);
        ticket.setCheckedInAt(OffsetDateTime.now());
        ticketRepository.save(ticket);

        firebaseSyncService.syncCheckInState(ticket.getTicketCode(), ticket.getCheckedInAt(), attemptsCount);
        
        return new ScanResponse(true, "✅ Valid! Welcome in.", ticket.getUser().getFullName());
    }
}
```

### 3.4. Drifter Vehicle Classification Engine
Administrative auto-tagging categories:

```java
public class VehicleClassifier {
    public static String detectVehicleClass(String make, String model, String modifications, String engineSpec) {
        String hay = String.format("%s %s %s %s", make, model, modifications, engineSpec).toLowerCase();
        
        // JDM Matching regex
        if (hay.matches(".*\\b(supra|skyline|silvia|rx-7|rx7|180sx|s15|s13|gt-r|gtr|evo|lancer evolution)\\b.*")) {
            return "JDM";
        }
        // Euro Matching regex
        if (hay.matches(".*\\b(bmw|mercedes|audi|porsche|volkswagen|vw|mini)\\b.*")) {
            return "Euro";
        }
        // Modification keywords matching
        if (hay.matches(".*\\b(stage|turbo|swap|widebody|kit|stance|drift|supercharger|forged|roll cage)\\b.*")) {
            return "Modified";
        }
        return "Stock";
    }
}
```

### 3.5. Dynamic Urgency Analytics Algorithm
Calculates urgent demand indicators by combining real database tallies with timestamp-derived offsets to display real-time counter bookings.

```java
@Service
@RequiredArgsConstructor
public class LiveStatsService {
    private final SpectatorTicketRepository ticketRepository;

    public LiveStatsResponse getLiveStats() {
        int databaseTotal = ticketRepository.countByStatusIn(List.of(TicketStatus.paid, TicketStatus.checked_in));
        
        OffsetDateTime since24h = OffsetDateTime.now().minusHours(24);
        int database24h = ticketRepository.countByStatusInAndBookedAtAfter(List.of(TicketStatus.paid, TicketStatus.checked_in), since24h);

        // Urgency Logic
        long nowMs = System.currentTimeMillis();
        long daysSinceLaunch = (nowMs - 1767225600000L) / (1000 * 60 * 60 * 24); // Launch base Jan 1 2026
        long seed = nowMs / (1000 * 60 * 30); // 30-min window changes
        
        int noise = (int)((seed % 47) - 20);
        int totalOffset = 2400 + (int)(daysSinceLaunch * 18) + noise;

        int hour = OffsetDateTime.now().getHour();
        boolean isActiveHours = (hour >= 10 && hour <= 23);
        int base24h = isActiveHours ? 180 : 40;
        int noise24h = (int)(seed % 31);
        int last24hOffset = base24h + noise24h;

        return new LiveStatsResponse(databaseTotal + totalOffset, database24h + last24hOffset);
    }
}
```

---

## 4. API Routing Contract

All endpoint requests expect headers: `Content-Type: application/json`. JWT routes expect `Authorization: Bearer <JWT_ACCESS_TOKEN>`.

### 4.1. General API Routing Specifications

| HTTP Method | Route Path | Access / Role | Request JSON Structure | Expected Response JSON | Description |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **POST** | `/api/auth/register` | Public | `{"full_name":"Jane Doe","email":"jane@example.com","password":"Pwd123","whatsapp":"9876543210"}` | `201 Created`<br>`{"success":true,"data":{"access_token":"...","user":{"id":"...","role":"user"}}}` | Register a new user |
| **POST** | `/api/auth/login` | Public | `{"email":"jane@example.com","password":"Pwd123"}` | `200 OK`<br>`{"success":true,"data":{"access_token":"...","user":{"id":"..."}}}` | Authenticate user |
| **POST** | `/api/auth/refresh` | Public | *Uses HTTP-only Cookie* `refresh_token` | `200 OK`<br>`{"success":true,"data":{"access_token":"..."}}` | Refresh access token |
| **POST** | `/api/tickets/checkout` | Authenticated | `{"items":[{"eventId":"...","tierId":"...","quantity":2}],"couponCode":"OFFER10","premiumSpiritsAcknowledged":true}` | `201 Created`<br>`{"payment_id":"...","amount":2549,"currency":"INR"}` | Checkout cart items |
| **POST** | `/api/tickets/scan` | Admin/Scanner | `{"code":"PASS-1A2B3C","password":"enicilion2026"}` | `200 OK`<br>`{"success":true,"data":{"valid":true,"message":"✅ Valid! Welcome in.","name":"Jane"}}` | Gate Scanner Entry verification |
| **POST** | `/api/coupons/validate` | Public | `{"code":"OFFER10","subtotal":2500,"items":[]}` | `200 OK`<br>`{"success":true,"data":{"code":"OFFER10","discountAmount":250,"total":2250}}` | Validate coupon discounts |
| **POST** | `/api/payments/razorpay/verify` | Authenticated | `{"payment_id":"...","razorpay_order_id":"...","razorpay_payment_id":"...","razorpay_signature":"..."}` | `200 OK`<br>`{"success":true,"data":{"status":"paid","paymentId":"..."}}` | Verify Razorpay signatures |
| **POST** | `/api/support/tickets` | Public | `{"category":"ticketing","message":"I need help","name":"Jane","phone":"9876543210"}` | `201 Created`<br>`{"id":"...","status":"open","createdAt":"..."}` | Submit help support tickets |

---

## 5. Integrations & Third-Party Service Specs

### 5.1. Firebase Admin Real-Time Synchronization
Check-in operations must immediately synchronize ticket statuses to Google Firebase Firestore under collection `/tickets/{ticketCode}`.

- **Firestore Document Path**: `/tickets/{ticketCode}`
- **Synchronized JSON Payload**:
  ```json
  {
    "status": "checked_in",
    "checkedInAt": "2026-06-13T17:35:00Z",
    "scanCount": 2,
    "blocked": false,
    "gate": "tickets_api",
    "operatorId": "scanner_terminal"
  }
  ```
- **Firebase Initialization Pattern**: Initialize using standard service account configurations passed via environment variable `FIREBASE_SERVICE_ACCOUNT` (JSON format).

### 5.2. ZeptoMail Transactional Email Integration
- **Vendor**: ZeptoMail / Zoho SMTP & HTTP API.
- **Trigger**: Successful payment callback (webhook).
- **Service Payload**: Send an email via ZeptoMail SMTP/HTTP containing details of bought tickets and **attach the generated ticket PDF buffer** with mime type `application/pdf`.

### 5.3. AiSensy WhatsApp Notification Integration
- **API Endpoint**: `https://backend.aisensy.com/dev/api/v2/channels/templates/send`
- **Method**: `POST`
- **Headers**: 
  - `Content-Type: application/json`
  - `Authorization: Bearer ${AISENSY_API_KEY}`
- **Payload Schema**:
  ```json
  {
    "apiKey": "${AISENSY_API_KEY}",
    "campaignName": "${AISENSY_CAMPAIGN_NAME}",
    "destination": "${destinationPhone}",
    "templateParams": [
      "${customerName}",
      "${ticketTier}",
      "${ticketCodes}",
      "https://enicilion.com/api/ticket-pdf/${firstTicketCode}"
    ],
    "source": "api-gateway"
  }
  ```

### 5.4. Image Processing & Compression (Drifter Application Showcase)
When drifter users submit vehicles, pictures uploaded through the multi-part form must be processed to prevent server directory storage attacks.
- **Constraints**: Limit sizes to `max 10MB` and check MIME types to verify they match image formats (`image/jpeg`, `image/png`, `image/webp`).
- **Processing**: Convert all formats on-the-fly to compressed `.webp` format (quality `80%`), with a maximum width of `1200px` (preserving aspect ratio) using TwelveMonkeys image filters. Save processed files under directory `/uploads/showcase/`.

### 5.5. PDF Ticket Generation Layout & Coordinates (OpenPDF Layout)
The PDF e-ticket must be generated in memory as an A4 page in **landscape** layout (842 x 595 px) matching the design system coordinate blueprint.

```text
A4 Landscape Canvas (842 x 595 pixels)
+--------------------------------------------------------------------------------+
|  Padding (32px)                                                                |
|  +--------------------+---------------------------------------+-------------+  |
|  | LEFT RAIL          | MAIN TICKET BODY                      | SCAN STUB   |  |
|  | width: 82px        | width: 472px                          | width: 192px|  |
|  |                    |                                       |             |  |
|  | Text rotated -90   | Gold Line separator                   | QR Code     |  |
|  | "MOTORSCAPE 2026"  | Event Name: "MOTORSCAPE 2026" (32pt)   | (138x138px) |  |
|  | Gold border        | Ticket Holder Name: "Jane Doe" (22pt) |             |  |
|  |                    | Pill Badge Category (Red background)  | Ticket Code |  |
|  |                    | Details Grid Table (2x2 cells)        | (13pt Deep) |  |
|  |                    | Venue Banner (directions link button) |             |  |
|  |                    |                                       |             |  |
|  +--------------------+---------------------------------------+-------------+  |
|                                                                                |
|  Footer T&C (7.5pt, Helvetica, dim text):                                      |
|  "By using this ticket, you agree to the Motorscape 2026 Terms & Conditions."  |
+--------------------------------------------------------------------------------+
```

- **Color Palettes (Hex values)**:
  - Background (Carbon Texture): `#0C0C0C` (with background pattern lines spaced by 6px)
  - Dark Panel (Cards): `#141414` / Border: `#262626`
  - Accent Color (Gold): `#C9A84C` (Gold line height: 2px)
  - Action/Branding (Red): `#EF3340` (Pill badges and Direction buttons)
  - Light Stub (Paper Coupon): `#F4F3EF` / QR Background: `#FFFFFF`
- **Page 2 (Terms & Conditions)**: Must be rendered in portrait orientation containing the 11 detailed terms (Validity, Verification, Refunds, Transfers, Security, F&B, Event Changes, Consent, Belongings, Liability, Support) in `#C8C8C8` 8pt Helvetica font.

---

## 6. Directory Layout & Build Specifications

### 6.1. Maven POM Configuration (`pom.xml`)
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.0</version>
        <relativePath/> 
    </parent>
    <groupId>com.enicilion</groupId>
    <artifactId>backend</artifactId>
    <version>1.0.0</version>
    <name>enicilion-backend</name>
    <description>Enterprise Spring Boot Platform for Enicilion</description>

    <properties>
        <java.version>21</java.version>
        <jjwt.version>0.12.5</jjwt.version>
        <org.mapstruct.version>1.5.5.Final</org.mapstruct.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
            <version>${jjwt.version}</version>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>com.google.firebase</groupId>
            <artifactId>firebase-admin</artifactId>
            <version>9.2.0</version>
        </dependency>
        <dependency>
            <groupId>org.mapstruct</groupId>
            <artifactId>mapstruct</artifactId>
            <version>${org.mapstruct.version}</version>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>com.github.librepdf</groupId>
            <artifactId>openpdf</artifactId>
            <version>2.0.2</version>
        </dependency>
        <!-- WebP and advanced image formats support -->
        <dependency>
            <groupId>com.twelvemonkeys.imageio</groupId>
            <artifactId>imageio-webp</artifactId>
            <version>3.10.1</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <source>21</source>
                    <target>21</target>
                    <annotationProcessorPaths>
                        <path>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                            <version>1.18.30</version>
                        </path>
                        <path>
                            <groupId>org.mapstruct</groupId>
                            <artifactId>mapstruct-processor</artifactId>
                            <version>${org.mapstruct.version}</version>
                        </path>
                    </annotationProcessorPaths>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

### 6.2. Directory Tree Layout
```text
com.enicilion.backend
│
├── config                  # Security filters, DB connections, Redis, and Firebase
│   ├── SecurityConfig.java
│   ├── RedisConfig.java
│   ├── FirebaseConfig.java
│   └── WebConfig.java      # Cors mapping, multipart configuration
│
├── common                  # Cross-cutting concerns, generic response payload formats
│   ├── base
│   │   └── BaseEntity.java
│   ├── exception
│   │   ├── GlobalExceptionHandler.java
│   │   ├── ResourceNotFoundException.java
│   │   └── InventoryExhaustedException.java
│   └── dto
│       └── ApiResponse.java
│
├── auth                    # Domain 1: Authentication & User Accounts
│   ├── controller
│   │   └── AuthController.java
│   ├── entity
│   │   └── User.java
│   ├── repository
│   │   └── UserRepository.java
│   └── service
│       ├── AuthService.java
│       └── JwtService.java
│
├── tickets                 # Domain 2: Ticketing, Scanners, Gate events, PDFs
│   ├── controller
│   │   └── TicketController.java
│   ├── entity
│   │   ├── TicketTier.java
│   │   ├── SpectatorTicket.java
│   │   └── CheckinEvent.java
│   ├── repository
│   │   └── TicketRepository.java
│   └── service
│       ├── TicketService.java
│       └── PdfService.java
│
├── applications            # Domain 3: Drifter/Media vehicle showcase submissions
│   ├── controller
│   │   └── ApplicationController.java
│   ├── entity
│   │   ├── Car.java
│   │   ├── DrifterApplication.java
│   │   ├── ApplicationCar.java
│   │   └── ApplicationPhoto.java
│   └── service
│       ├── ApplicationService.java
│       └── ImageProcessingService.java
│
├── coupons                 # Domain 4: Coupon code checkout discounts & reservations
│   ├── controller
│   │   └── CouponController.java
│   ├── entity
│   │   └── Coupon.java
│   └── service
│       └── CouponService.java
│
├── support                 # Domain 5: Support helpdesk queries
│   ├── controller
│   │   └── SupportController.java
│   ├── entity
│   │   └── SupportTicket.java
│   └── service
│       └── SupportService.java
│
└── payments                # Domain 6: Razorpay, Paypal checkouts webhooks verification
    ├── controller
    │   └── PaymentController.java
    ├── entity
    │   └── Payment.java
    └── service
        ├── RazorpayService.java
        └── PayPalService.java
```

### 6.3. Spring Boot Configuration (`application.yml`)
```yaml
server:
  port: 4000
  tomcat:
    threads:
      max: 200
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 15MB

spring:
  datasource:
    url: ${DATABASE_URL:jdbc:postgresql://localhost:5432/enicilion}
    username: ${DATABASE_USERNAME:postgres}
    password: ${DATABASE_PASSWORD:postgres}
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 30
      minimum-idle: 10
      idle-timeout: 30000
      pool-name: EnicilionHikariPool

  jpa:
    database-platform: org.hibernate.dialect.PostgreSQLDialect
    hibernate:
      ddl-auto: update
    show-sql: false
    properties:
      hibernate:
        format_sql: true

  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}

app:
  jwt:
    access-secret: ${JWT_ACCESS_SECRET}
    refresh-secret: ${JWT_REFRESH_SECRET}
    access-expiration-ms: 900000      # 15 minutes
    refresh-expiration-ms: 604800000   # 7 days
  scanner:
    password: ${SCANNER_PASSWORD:enicilion2026}
  firebase:
    service-account-json: ${FIREBASE_SERVICE_ACCOUNT}
  razorpay:
    key-id: ${RAZORPAY_KEY_ID}
    key-secret: ${RAZORPAY_KEY_SECRET}
    webhook-secret: ${RAZORPAY_WEBHOOK_SECRET}
  zeptomail:
    api-key: ${ZEPTOMAIL_API_KEY}
    sender-email: ${ZEPTOMAIL_SENDER:tickets@enicilion.com}
  aisensy:
    api-key: ${AISENSY_API_KEY}
    campaign-name: ${AISENSY_TICKET_CAMPAIGN:motorscape_ticket_confirm}

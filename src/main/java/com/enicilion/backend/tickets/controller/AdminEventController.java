package com.enicilion.backend.tickets.controller;

import com.enicilion.backend.auth.entity.User;
import com.enicilion.backend.auth.entity.UserRole;
import com.enicilion.backend.auth.repository.UserRepository;
import com.enicilion.backend.common.dto.ApiResponse;
import com.enicilion.backend.common.exception.BadValidationException;
import com.enicilion.backend.common.exception.ResourceNotFoundException;
import com.enicilion.backend.common.exception.UnauthorizedException;
import com.enicilion.backend.auth.service.SecurityContextService;
import com.enicilion.backend.organizers.entity.Organizer;
import com.enicilion.backend.organizers.repository.OrganizerRepository;
import com.enicilion.backend.tickets.entity.Event;
import com.enicilion.backend.tickets.entity.EventStatus;
import com.enicilion.backend.tickets.entity.EventSummary;
import com.enicilion.backend.tickets.entity.TicketTier;
import com.enicilion.backend.tickets.repository.EventRepository;
import com.enicilion.backend.tickets.repository.TicketTierRepository;
import com.enicilion.backend.tickets.service.EventSettlementService;
import com.enicilion.backend.tickets.service.RedisInventoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/events")
@RequiredArgsConstructor
public class AdminEventController {

    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final TicketTierRepository ticketTierRepository;
    private final OrganizerRepository organizerRepository;
    private final RedisInventoryService redisInventoryService;
    private final EventSettlementService eventSettlementService;
    private final SecurityContextService securityContextService;



    private void checkEventAccess(User user, Event event) {
        if (user.getOrganizer() != null) {
            if (event.getOrganizer() == null || !event.getOrganizer().getId().equals(user.getOrganizer().getId())) {
                throw new UnauthorizedException("Access denied. This event belongs to another organizer.");
            }
        }
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Event>>> listEvents() {
        User user = securityContextService.getCurrentUser();
        securityContextService.checkAdminOrStaff(user);

        List<Event> events;
        if (user.getOrganizer() != null) {
            events = eventRepository.findByOrganizerId(user.getOrganizer().getId());
        } else {
            events = eventRepository.findAll();
        }
        return ResponseEntity.ok(ApiResponse.success(events));
    }

    @PostMapping
    @Transactional
    @org.springframework.cache.annotation.CacheEvict(value = {"public_events", "public_events_slug"}, allEntries = true)
    public ResponseEntity<ApiResponse<Event>> createEvent(@Valid @RequestBody EventRequest request) {
        User user = securityContextService.getCurrentUser();
        securityContextService.checkAdminOrStaff(user);

        if (eventRepository.existsBySlug(request.getSlug())) {
            throw new BadValidationException("An event with slug '" + request.getSlug() + "' already exists.");
        }

        Organizer organizer = null;
        if (user.getOrganizer() != null) {
            organizer = user.getOrganizer();
        } else if (request.getOrganizerId() != null) {
            organizer = organizerRepository.findById(request.getOrganizerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Organizer not found with ID: " + request.getOrganizerId()));
        }

        Event event = Event.builder()
                .name(request.getName())
                .slug(request.getSlug())
                .description(request.getDescription())
                .location(request.getLocation())
                .eventDate(request.getEventDate())
                .applicationsOpenAt(request.getApplicationsOpenAt())
                .applicationsCloseAt(request.getApplicationsCloseAt())
                .ticketsOpenAt(request.getTicketsOpenAt())
                .maxDrifters(request.getMaxDrifters())
                .maxSpectators(request.getMaxSpectators())
                .drifterFee(request.getDrifterFee() != null ? request.getDrifterFee() : BigDecimal.ZERO)
                .currency(request.getCurrency() != null ? request.getCurrency() : "INR")
                .status(request.getStatus() != null ? request.getStatus() : EventStatus.draft)
                .registrationSchema(request.getRegistrationSchema())
                .organizer(organizer)
                .creator(user)
                .build();

        Event savedEvent = eventRepository.save(event);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(savedEvent));
    }

    @PutMapping("/{id}")
    @Transactional
    @org.springframework.cache.annotation.CacheEvict(value = {"public_events", "public_events_slug"}, allEntries = true)
    public ResponseEntity<ApiResponse<Event>> updateEvent(
            @PathVariable("id") UUID id,
            @Valid @RequestBody EventRequest request) {
        User user = securityContextService.getCurrentUser();
        securityContextService.checkAdminOrStaff(user);

        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with ID: " + id));

        checkEventAccess(user, event);

        if (eventRepository.existsBySlugAndIdNot(request.getSlug(), id)) {
            throw new BadValidationException("An event with slug '" + request.getSlug() + "' already exists.");
        }

        Organizer organizer = event.getOrganizer();
        if (user.getOrganizer() == null && request.getOrganizerId() != null) {
            organizer = organizerRepository.findById(request.getOrganizerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Organizer not found with ID: " + request.getOrganizerId()));
        }

        event.setName(request.getName());
        event.setSlug(request.getSlug());
        event.setDescription(request.getDescription());
        event.setLocation(request.getLocation());
        event.setEventDate(request.getEventDate());
        event.setApplicationsOpenAt(request.getApplicationsOpenAt());
        event.setApplicationsCloseAt(request.getApplicationsCloseAt());
        event.setTicketsOpenAt(request.getTicketsOpenAt());
        event.setMaxDrifters(request.getMaxDrifters());
        event.setMaxSpectators(request.getMaxSpectators());
        if (request.getDrifterFee() != null) {
            event.setDrifterFee(request.getDrifterFee());
        }
        if (request.getCurrency() != null) {
            event.setCurrency(request.getCurrency());
        }
        if (request.getStatus() != null) {
            event.setStatus(request.getStatus());
        }
        event.setRegistrationSchema(request.getRegistrationSchema());
        event.setOrganizer(organizer);

        Event updatedEvent = eventRepository.save(event);
        return ResponseEntity.ok(ApiResponse.success(updatedEvent));
    }

    @DeleteMapping("/{id}")
    @Transactional
    @org.springframework.cache.annotation.CacheEvict(value = {"public_events", "public_events_slug"}, allEntries = true)
    public ResponseEntity<ApiResponse<Void>> deleteEvent(@PathVariable("id") UUID id) {
        User user = securityContextService.getCurrentUser();
        securityContextService.checkAdminOrStaff(user);

        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with ID: " + id));

        checkEventAccess(user, event);

        // Fetch associated tiers so we can clean up Redis inventory keys before deletion
        List<TicketTier> tiers = ticketTierRepository.findByEventId(id);
        for (TicketTier tier : tiers) {
            redisInventoryService.deleteInventory(tier.getId());
        }

        eventRepository.delete(event);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/{id}/tiers")
    public ResponseEntity<ApiResponse<List<TicketTier>>> listTiers(@PathVariable("id") UUID eventId) {
        User user = securityContextService.getCurrentUser();
        securityContextService.checkAdminOrStaff(user);

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with ID: " + eventId));

        checkEventAccess(user, event);

        List<TicketTier> tiers = ticketTierRepository.findByEventId(eventId);
        return ResponseEntity.ok(ApiResponse.success(tiers));
    }

    @PostMapping("/{id}/tiers")
    @Transactional
    @org.springframework.cache.annotation.CacheEvict(value = {"public_events", "public_events_slug"}, allEntries = true)
    public ResponseEntity<ApiResponse<TicketTier>> createTier(
            @PathVariable("id") UUID eventId,
            @Valid @RequestBody TicketTierRequest request) {
        User user = securityContextService.getCurrentUser();
        securityContextService.checkAdminOrStaff(user);

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with ID: " + eventId));

        checkEventAccess(user, event);

        TicketTier tier = TicketTier.builder()
                .event(event)
                .name(request.getName())
                .price(request.getPrice())
                .quantity(request.getQuantity())
                .description(request.getDescription())
                .isPublic(request.isPublic())
                .build();

        TicketTier savedTier = ticketTierRepository.save(tier);
        redisInventoryService.syncInventory(savedTier.getId());

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(savedTier));
    }

    @PutMapping("/{eventId}/tiers/{tierId}")
    @Transactional
    @org.springframework.cache.annotation.CacheEvict(value = {"public_events", "public_events_slug"}, allEntries = true)
    public ResponseEntity<ApiResponse<TicketTier>> updateTier(
            @PathVariable("eventId") UUID eventId,
            @PathVariable("tierId") UUID tierId,
            @Valid @RequestBody TicketTierRequest request) {
        User user = securityContextService.getCurrentUser();
        securityContextService.checkAdminOrStaff(user);

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with ID: " + eventId));

        checkEventAccess(user, event);

        TicketTier tier = ticketTierRepository.findById(tierId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket tier not found with ID: " + tierId));

        if (!tier.getEvent().getId().equals(eventId)) {
            throw new BadValidationException("Ticket tier does not belong to this event.");
        }

        tier.setName(request.getName());
        tier.setPrice(request.getPrice());
        tier.setQuantity(request.getQuantity());
        tier.setDescription(request.getDescription());
        tier.setPublic(request.isPublic());

        TicketTier updatedTier = ticketTierRepository.save(tier);
        redisInventoryService.syncInventory(updatedTier.getId());

        return ResponseEntity.ok(ApiResponse.success(updatedTier));
    }

    @DeleteMapping("/{eventId}/tiers/{tierId}")
    @Transactional
    @org.springframework.cache.annotation.CacheEvict(value = {"public_events", "public_events_slug"}, allEntries = true)
    public ResponseEntity<ApiResponse<Void>> deleteTier(
            @PathVariable("eventId") UUID eventId,
            @PathVariable("tierId") UUID tierId) {
        User user = securityContextService.getCurrentUser();
        securityContextService.checkAdminOrStaff(user);

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with ID: " + eventId));

        checkEventAccess(user, event);

        TicketTier tier = ticketTierRepository.findById(tierId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket tier not found with ID: " + tierId));

        if (!tier.getEvent().getId().equals(eventId)) {
            throw new BadValidationException("Ticket tier does not belong to this event.");
        }

        ticketTierRepository.delete(tier);
        redisInventoryService.deleteInventory(tierId);

        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/{id}/settle")
    @Transactional
    public ResponseEntity<ApiResponse<EventSummary>> settleEvent(@PathVariable("id") UUID id) {
        User user = securityContextService.getCurrentUser();
        securityContextService.checkAdminOrStaff(user);

        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with ID: " + id));

        checkEventAccess(user, event);

        EventSummary summary = eventSettlementService.settleEvent(id);
        return ResponseEntity.ok(ApiResponse.success(summary));
    }

    @GetMapping("/{id}/settle")
    public ResponseEntity<ApiResponse<EventSummary>> getSettlement(@PathVariable("id") UUID id) {
        User user = securityContextService.getCurrentUser();
        securityContextService.checkAdminOrStaff(user);

        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with ID: " + id));

        checkEventAccess(user, event);

        EventSummary summary = eventSettlementService.getSettlementSummary(id);
        if (summary == null) {
            throw new ResourceNotFoundException("Settlement summary not found for this event.");
        }
        return ResponseEntity.ok(ApiResponse.success(summary));
    }

    @Data
    public static class EventRequest {
        @NotBlank(message = "Name is required")
        private String name;

        @NotBlank(message = "Slug is required")
        @Pattern(regexp = "^[a-z0-9-]+$", message = "Slug must be lowercase alphanumeric and hyphens only")
        private String slug;

        private String description;

        @NotBlank(message = "Location is required")
        private String location;

        @NotNull(message = "Event date is required")
        private OffsetDateTime eventDate;

        private OffsetDateTime applicationsOpenAt;
        private OffsetDateTime applicationsCloseAt;
        private OffsetDateTime ticketsOpenAt;

        private Integer maxDrifters;
        private Integer maxSpectators;

        private BigDecimal drifterFee;
        private String currency;

        private EventStatus status;

        private String registrationSchema;

        private UUID organizerId;
    }

    @Data
    public static class TicketTierRequest {
        @NotBlank(message = "Name is required")
        private String name;

        @NotNull(message = "Price is required")
        @Min(value = 0, message = "Price must be positive or zero")
        private BigDecimal price;

        private Integer quantity;

        private String description;

        private boolean isPublic = true;
    }
}

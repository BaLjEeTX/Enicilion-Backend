package com.enicilion.backend.tickets.controller;

import com.enicilion.backend.common.dto.ApiResponse;
import com.enicilion.backend.common.exception.ResourceNotFoundException;
import com.enicilion.backend.tickets.entity.Event;
import com.enicilion.backend.tickets.entity.TicketTier;
import com.enicilion.backend.tickets.repository.EventRepository;
import com.enicilion.backend.tickets.repository.TicketTierRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final com.enicilion.backend.tickets.service.PublicEventService publicEventService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Event>>> getAllPublicEvents() {
        return ResponseEntity.ok(publicEventService.getPublicEvents());
    }

    @GetMapping("/{slug}")
    public ResponseEntity<ApiResponse<EventDetailDto>> getEventBySlug(@PathVariable("slug") String slug) {
        return ResponseEntity.ok(publicEventService.getEventBySlug(slug));
    }

    @Data
    @Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class EventDetailDto {
        private UUID id;
        private String name;
        private String slug;
        private String description;
        private String location;
        private OffsetDateTime eventDate;
        private String registrationSchema;
        private List<TicketTierDto> tiers;
    }

    @Data
    @Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class TicketTierDto {
        private UUID id;
        private String name;
        private BigDecimal price;
        private Integer quantity;
        private String description;
    }
}

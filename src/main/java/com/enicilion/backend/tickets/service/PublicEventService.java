package com.enicilion.backend.tickets.service;

import com.enicilion.backend.common.dto.ApiResponse;
import com.enicilion.backend.common.exception.ResourceNotFoundException;
import com.enicilion.backend.tickets.entity.Event;
import com.enicilion.backend.tickets.entity.TicketTier;
import com.enicilion.backend.tickets.repository.EventRepository;
import com.enicilion.backend.tickets.repository.TicketTierRepository;
import com.enicilion.backend.tickets.controller.EventController.EventDetailDto;
import com.enicilion.backend.tickets.controller.EventController.TicketTierDto;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PublicEventService {

    private final EventRepository eventRepository;
    private final TicketTierRepository ticketTierRepository;

    @Cacheable(value = "public_events")
    public ApiResponse<List<Event>> getPublicEvents() {
        List<Event> events = eventRepository.findAll().stream()
                .filter(e -> e.getStatus() == com.enicilion.backend.tickets.entity.EventStatus.published || 
                             e.getStatus() == com.enicilion.backend.tickets.entity.EventStatus.ongoing)
                .collect(Collectors.toList());
        return ApiResponse.success(events);
    }

    @Cacheable(value = "public_events_slug", key = "#slug")
    public ApiResponse<EventDetailDto> getEventBySlug(String slug) {
        Event event = eventRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with slug: " + slug));

        List<TicketTier> tiers = ticketTierRepository.findByEventId(event.getId());
        List<TicketTierDto> tierDtos = tiers.stream()
                .filter(TicketTier::isPublic)
                .map(t -> TicketTierDto.builder()
                        .id(t.getId())
                        .name(t.getName())
                        .price(t.getPrice())
                        .quantity(t.getQuantity())
                        .description(t.getDescription())
                        .build())
                .collect(Collectors.toList());

        EventDetailDto dto = EventDetailDto.builder()
                .id(event.getId())
                .name(event.getName())
                .slug(event.getSlug())
                .description(event.getDescription())
                .location(event.getLocation())
                .eventDate(event.getEventDate())
                .registrationSchema(event.getRegistrationSchema())
                .tiers(tierDtos)
                .build();

        return ApiResponse.success(dto);
    }
}

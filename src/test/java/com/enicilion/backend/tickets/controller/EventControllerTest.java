package com.enicilion.backend.tickets.controller;

import com.enicilion.backend.common.dto.ApiResponse;
import com.enicilion.backend.common.exception.ResourceNotFoundException;
import com.enicilion.backend.tickets.entity.Event;
import com.enicilion.backend.tickets.entity.TicketTier;
import com.enicilion.backend.tickets.repository.EventRepository;
import com.enicilion.backend.tickets.repository.TicketTierRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EventControllerTest {

    @Mock
    private com.enicilion.backend.tickets.service.PublicEventService publicEventService;

    @InjectMocks
    private EventController eventController;

    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        closeable.close();
    }

    @Test
    void testGetEventBySlug_Success() {
        String slug = "test-event";
        UUID eventId = UUID.randomUUID();
        
        EventController.EventDetailDto detailDto = EventController.EventDetailDto.builder()
                .id(eventId)
                .name("Test Event")
                .slug(slug)
                .description("A test event description")
                .location("Test Location")
                .eventDate(OffsetDateTime.now())
                .registrationSchema("{}")
                .tiers(List.of(EventController.TicketTierDto.builder()
                        .id(UUID.randomUUID())
                        .name("VIP")
                        .price(BigDecimal.valueOf(100.0))
                        .quantity(50)
                        .description("VIP Pass")
                        .build()))
                .build();

        when(publicEventService.getEventBySlug(slug)).thenReturn(ApiResponse.success(detailDto));

        ResponseEntity<ApiResponse<EventController.EventDetailDto>> response = eventController.getEventBySlug(slug);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        
        EventController.EventDetailDto detail = response.getBody().getData();
        assertNotNull(detail);
        assertEquals(eventId, detail.getId());
        assertEquals("Test Event", detail.getName());
        assertEquals(slug, detail.getSlug());
        
        assertEquals(1, detail.getTiers().size());
        EventController.TicketTierDto tierDto = detail.getTiers().get(0);
        assertEquals("VIP", tierDto.getName());
        assertEquals(BigDecimal.valueOf(100.0), tierDto.getPrice());
        assertEquals(50, tierDto.getQuantity());
        assertEquals("VIP Pass", tierDto.getDescription());

        verify(publicEventService, times(1)).getEventBySlug(slug);
    }

    @Test
    void testGetEventBySlug_NotFound() {
        String slug = "non-existent-event";
        when(publicEventService.getEventBySlug(slug)).thenThrow(new ResourceNotFoundException("Event not found with slug: " + slug));

        assertThrows(ResourceNotFoundException.class, () -> eventController.getEventBySlug(slug));
        
        verify(publicEventService, times(1)).getEventBySlug(slug);
    }

    @Test
    void testGetAllPublicEvents_Success() {
        Event event1 = new Event();
        event1.setName("Published Event");
        event1.setStatus(com.enicilion.backend.tickets.entity.EventStatus.published);

        Event event2 = new Event();
        event2.setName("Ongoing Event");
        event2.setStatus(com.enicilion.backend.tickets.entity.EventStatus.ongoing);

        when(publicEventService.getPublicEvents()).thenReturn(ApiResponse.success(List.of(event1, event2)));

        ResponseEntity<ApiResponse<List<Event>>> response = eventController.getAllPublicEvents();

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        
        List<Event> result = response.getBody().getData();
        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(e -> e.getName().equals("Published Event")));
        assertTrue(result.stream().anyMatch(e -> e.getName().equals("Ongoing Event")));
        verify(publicEventService, times(1)).getPublicEvents();
    }
}

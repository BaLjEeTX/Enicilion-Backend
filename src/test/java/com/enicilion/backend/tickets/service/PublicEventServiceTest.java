package com.enicilion.backend.tickets.service;

import com.enicilion.backend.common.dto.ApiResponse;
import com.enicilion.backend.common.exception.ResourceNotFoundException;
import com.enicilion.backend.tickets.entity.Event;
import com.enicilion.backend.tickets.entity.TicketTier;
import com.enicilion.backend.tickets.repository.EventRepository;
import com.enicilion.backend.tickets.repository.TicketTierRepository;
import com.enicilion.backend.tickets.controller.EventController.EventDetailDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PublicEventServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private TicketTierRepository ticketTierRepository;

    @InjectMocks
    private PublicEventService publicEventService;

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
    void testGetPublicEvents_Success() {
        Event event1 = new Event();
        event1.setName("Published Event");
        event1.setStatus(com.enicilion.backend.tickets.entity.EventStatus.published);

        Event event2 = new Event();
        event2.setName("Ongoing Event");
        event2.setStatus(com.enicilion.backend.tickets.entity.EventStatus.ongoing);

        Event event3 = new Event();
        event3.setName("Draft Event");
        event3.setStatus(com.enicilion.backend.tickets.entity.EventStatus.draft);

        when(eventRepository.findAll()).thenReturn(List.of(event1, event2, event3));

        ApiResponse<List<Event>> response = publicEventService.getPublicEvents();

        assertNotNull(response);
        assertTrue(response.isSuccess());
        
        List<Event> result = response.getData();
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(e -> e.getName().equals("Published Event")));
        assertTrue(result.stream().anyMatch(e -> e.getName().equals("Ongoing Event")));
        assertFalse(result.stream().anyMatch(e -> e.getName().equals("Draft Event")));
    }

    @Test
    void testGetEventBySlug_Success() {
        String slug = "test-event";
        UUID eventId = UUID.randomUUID();
        
        Event mockEvent = new Event();
        mockEvent.setId(eventId);
        mockEvent.setName("Test Event");
        mockEvent.setSlug(slug);
        mockEvent.setDescription("A test event description");
        mockEvent.setLocation("Test Location");
        mockEvent.setEventDate(OffsetDateTime.now());
        mockEvent.setRegistrationSchema("{}");

        TicketTier publicTier = new TicketTier();
        publicTier.setId(UUID.randomUUID());
        publicTier.setName("VIP");
        publicTier.setPrice(BigDecimal.valueOf(100.0));
        publicTier.setQuantity(50);
        publicTier.setPublic(true);
        publicTier.setDescription("VIP Pass");

        TicketTier privateTier = new TicketTier();
        privateTier.setId(UUID.randomUUID());
        privateTier.setName("Hidden");
        privateTier.setPrice(BigDecimal.valueOf(50.0));
        privateTier.setQuantity(20);
        privateTier.setPublic(false);
        privateTier.setDescription("Staff Pass");

        when(eventRepository.findBySlug(slug)).thenReturn(Optional.of(mockEvent));
        when(ticketTierRepository.findByEventId(eventId)).thenReturn(List.of(publicTier, privateTier));

        ApiResponse<EventDetailDto> response = publicEventService.getEventBySlug(slug);

        assertNotNull(response);
        assertTrue(response.isSuccess());
        
        EventDetailDto detail = response.getData();
        assertNotNull(detail);
        assertEquals(eventId, detail.getId());
        assertEquals("Test Event", detail.getName());
        assertEquals(slug, detail.getSlug());
        
        assertEquals(1, detail.getTiers().size());
        assertEquals("VIP", detail.getTiers().get(0).getName());
    }

    @Test
    void testGetEventBySlug_NotFound() {
        String slug = "non-existent-event";
        when(eventRepository.findBySlug(slug)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> publicEventService.getEventBySlug(slug));
    }
}

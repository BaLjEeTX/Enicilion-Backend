package com.enicilion.backend.tickets.controller;

import com.enicilion.backend.auth.entity.User;
import com.enicilion.backend.auth.entity.UserRole;
import com.enicilion.backend.auth.repository.UserRepository;
import com.enicilion.backend.common.dto.ApiResponse;
import com.enicilion.backend.common.exception.BadValidationException;
import com.enicilion.backend.common.exception.ResourceNotFoundException;
import com.enicilion.backend.common.exception.UnauthorizedException;
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
import com.enicilion.backend.auth.service.SecurityContextService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminEventControllerTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private TicketTierRepository ticketTierRepository;
    @Mock
    private OrganizerRepository organizerRepository;
    @Mock
    private RedisInventoryService redisInventoryService;
    @Mock
    private EventSettlementService eventSettlementService;
    @Mock
    private SecurityContext securityContext;
    @Mock
    private Authentication authentication;
    @Mock
    private SecurityContextService securityContextService;

    @InjectMocks
    private AdminEventController adminEventController;

    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(securityContextService.getCurrentUser()).thenAnswer(inv -> {
            String email = SecurityContextHolder.getContext().getAuthentication().getName();
            return userRepository.findByEmail(email)
                    .orElseThrow(() -> new UnauthorizedException("Authenticated user not found."));
        });
        doAnswer(inv -> {
            User u = inv.getArgument(0);
            if (u.getRole() != UserRole.admin && u.getRole() != UserRole.staff) {
                throw new UnauthorizedException("Access denied.");
            }
            return null;
        }).when(securityContextService).checkAdminOrStaff(any(User.class));
        doAnswer(inv -> {
            User u = inv.getArgument(0);
            if (u.getRole() != UserRole.admin) {
                throw new UnauthorizedException("Access denied.");
            }
            return null;
        }).when(securityContextService).checkAdminOnly(any(User.class));
    }

    @AfterEach
    void tearDown() throws Exception {
        SecurityContextHolder.clearContext();
        closeable.close();
    }

    private User mockAdminUser(Organizer organizer) {
        User user = new User();
        user.setEmail("admin@test.com");
        user.setRole(UserRole.admin);
        user.setOrganizer(organizer);
        when(authentication.getName()).thenReturn("admin@test.com");
        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(user));
        return user;
    }

    private User mockNormalUser() {
        User user = new User();
        user.setEmail("user@test.com");
        user.setRole(UserRole.user);
        when(authentication.getName()).thenReturn("user@test.com");
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        return user;
    }

    @Test
    void testListEvents_GlobalAdmin() {
        mockAdminUser(null);
        Event event = new Event();
        event.setName("Global Event");
        when(eventRepository.findAll()).thenReturn(List.of(event));

        ResponseEntity<ApiResponse<List<Event>>> response = adminEventController.listEvents();

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().getData().size());
        assertEquals("Global Event", response.getBody().getData().get(0).getName());
        verify(eventRepository, times(1)).findAll();
        verify(eventRepository, never()).findByOrganizerId(any());
    }

    @Test
    void testListEvents_TenantAdmin() {
        Organizer organizer = new Organizer();
        organizer.setId(UUID.randomUUID());
        mockAdminUser(organizer);

        Event event = new Event();
        event.setName("Tenant Event");
        when(eventRepository.findByOrganizerId(organizer.getId())).thenReturn(List.of(event));

        ResponseEntity<ApiResponse<List<Event>>> response = adminEventController.listEvents();

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().getData().size());
        assertEquals("Tenant Event", response.getBody().getData().get(0).getName());
        verify(eventRepository, times(1)).findByOrganizerId(organizer.getId());
        verify(eventRepository, never()).findAll();
    }

    @Test
    void testListEvents_Unauthorized() {
        mockNormalUser();
        assertThrows(UnauthorizedException.class, () -> adminEventController.listEvents());
    }

    @Test
    void testCreateEvent_Success() {
        Organizer organizer = new Organizer();
        organizer.setId(UUID.randomUUID());
        User user = mockAdminUser(organizer);

        AdminEventController.EventRequest request = new AdminEventController.EventRequest();
        request.setName("New Event");
        request.setSlug("new-event");
        request.setLocation("BIC");
        request.setEventDate(OffsetDateTime.now());
        request.setStatus(EventStatus.draft);

        when(eventRepository.existsBySlug("new-event")).thenReturn(false);
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<ApiResponse<Event>> response = adminEventController.createEvent(request);

        assertNotNull(response);
        assertEquals(201, response.getStatusCode().value());
        Event saved = response.getBody().getData();
        assertNotNull(saved);
        assertEquals("New Event", saved.getName());
        assertEquals(organizer, saved.getOrganizer());
        assertEquals(user, saved.getCreator());
        verify(eventRepository, times(1)).save(any(Event.class));
    }

    @Test
    void testCreateEvent_DuplicateSlug() {
        mockAdminUser(null);

        AdminEventController.EventRequest request = new AdminEventController.EventRequest();
        request.setSlug("existing-event");

        when(eventRepository.existsBySlug("existing-event")).thenReturn(true);

        assertThrows(BadValidationException.class, () -> adminEventController.createEvent(request));
        verify(eventRepository, never()).save(any());
    }

    @Test
    void testUpdateEvent_Success() {
        Organizer organizer = new Organizer();
        organizer.setId(UUID.randomUUID());
        mockAdminUser(organizer);

        UUID eventId = UUID.randomUUID();
        Event existingEvent = new Event();
        existingEvent.setId(eventId);
        existingEvent.setName("Old Name");
        existingEvent.setOrganizer(organizer);

        AdminEventController.EventRequest request = new AdminEventController.EventRequest();
        request.setName("Updated Name");
        request.setSlug("updated-slug");
        request.setLocation("Updated Loc");
        request.setEventDate(OffsetDateTime.now());

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(existingEvent));
        when(eventRepository.existsBySlugAndIdNot("updated-slug", eventId)).thenReturn(false);
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<ApiResponse<Event>> response = adminEventController.updateEvent(eventId, request);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("Updated Name", response.getBody().getData().getName());
        verify(eventRepository, times(1)).save(existingEvent);
    }

    @Test
    void testUpdateEvent_AccessDenied() {
        Organizer organizer1 = new Organizer();
        organizer1.setId(UUID.randomUUID());
        mockAdminUser(organizer1);

        Organizer organizer2 = new Organizer();
        organizer2.setId(UUID.randomUUID());

        UUID eventId = UUID.randomUUID();
        Event existingEvent = new Event();
        existingEvent.setId(eventId);
        existingEvent.setOrganizer(organizer2);

        AdminEventController.EventRequest request = new AdminEventController.EventRequest();

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(existingEvent));

        assertThrows(UnauthorizedException.class, () -> adminEventController.updateEvent(eventId, request));
        verify(eventRepository, never()).save(any());
    }

    @Test
    void testDeleteEvent_Success() {
        Organizer organizer = new Organizer();
        organizer.setId(UUID.randomUUID());
        mockAdminUser(organizer);

        UUID eventId = UUID.randomUUID();
        Event existingEvent = new Event();
        existingEvent.setId(eventId);
        existingEvent.setOrganizer(organizer);

        TicketTier tier = new TicketTier();
        tier.setId(UUID.randomUUID());

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(existingEvent));
        when(ticketTierRepository.findByEventId(eventId)).thenReturn(List.of(tier));

        ResponseEntity<ApiResponse<Void>> response = adminEventController.deleteEvent(eventId);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        verify(redisInventoryService, times(1)).deleteInventory(tier.getId());
        verify(eventRepository, times(1)).delete(existingEvent);
    }

    @Test
    void testCreateTier_Success() {
        Organizer organizer = new Organizer();
        organizer.setId(UUID.randomUUID());
        mockAdminUser(organizer);

        UUID eventId = UUID.randomUUID();
        Event event = new Event();
        event.setId(eventId);
        event.setOrganizer(organizer);

        AdminEventController.TicketTierRequest request = new AdminEventController.TicketTierRequest();
        request.setName("VIP");
        request.setPrice(BigDecimal.valueOf(100));
        request.setQuantity(50);

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(ticketTierRepository.save(any(TicketTier.class))).thenAnswer(invocation -> {
            TicketTier t = invocation.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });

        ResponseEntity<ApiResponse<TicketTier>> response = adminEventController.createTier(eventId, request);

        assertNotNull(response);
        assertEquals(201, response.getStatusCode().value());
        TicketTier created = response.getBody().getData();
        assertNotNull(created);
        assertEquals("VIP", created.getName());
        verify(redisInventoryService, times(1)).syncInventory(created.getId());
    }

    @Test
    void testUpdateTier_Success() {
        Organizer organizer = new Organizer();
        organizer.setId(UUID.randomUUID());
        mockAdminUser(organizer);

        UUID eventId = UUID.randomUUID();
        Event event = new Event();
        event.setId(eventId);
        event.setOrganizer(organizer);

        UUID tierId = UUID.randomUUID();
        TicketTier existingTier = new TicketTier();
        existingTier.setId(tierId);
        existingTier.setEvent(event);
        existingTier.setName("Old Name");

        AdminEventController.TicketTierRequest request = new AdminEventController.TicketTierRequest();
        request.setName("New Name");
        request.setPrice(BigDecimal.valueOf(150));

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(ticketTierRepository.findById(tierId)).thenReturn(Optional.of(existingTier));
        when(ticketTierRepository.save(any(TicketTier.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<ApiResponse<TicketTier>> response = adminEventController.updateTier(eventId, tierId, request);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("New Name", response.getBody().getData().getName());
        verify(redisInventoryService, times(1)).syncInventory(tierId);
    }

    @Test
    void testDeleteTier_Success() {
        Organizer organizer = new Organizer();
        organizer.setId(UUID.randomUUID());
        mockAdminUser(organizer);

        UUID eventId = UUID.randomUUID();
        Event event = new Event();
        event.setId(eventId);
        event.setOrganizer(organizer);

        UUID tierId = UUID.randomUUID();
        TicketTier tier = new TicketTier();
        tier.setId(tierId);
        tier.setEvent(event);

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(ticketTierRepository.findById(tierId)).thenReturn(Optional.of(tier));

        ResponseEntity<ApiResponse<Void>> response = adminEventController.deleteTier(eventId, tierId);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        verify(ticketTierRepository, times(1)).delete(tier);
        verify(redisInventoryService, times(1)).deleteInventory(tierId);
    }

    @Test
    void testSettleEvent_Success() {
        Organizer organizer = new Organizer();
        organizer.setId(UUID.randomUUID());
        mockAdminUser(organizer);

        UUID eventId = UUID.randomUUID();
        Event event = new Event();
        event.setId(eventId);
        event.setOrganizer(organizer);

        EventSummary summary = new EventSummary();
        summary.setId(UUID.randomUUID());

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(eventSettlementService.settleEvent(eventId)).thenReturn(summary);

        ResponseEntity<ApiResponse<EventSummary>> response = adminEventController.settleEvent(eventId);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(summary, response.getBody().getData());
        verify(eventSettlementService, times(1)).settleEvent(eventId);
    }

    @Test
    void testGetSettlement_Success() {
        Organizer organizer = new Organizer();
        organizer.setId(UUID.randomUUID());
        mockAdminUser(organizer);

        UUID eventId = UUID.randomUUID();
        Event event = new Event();
        event.setId(eventId);
        event.setOrganizer(organizer);

        EventSummary summary = new EventSummary();
        summary.setId(UUID.randomUUID());

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(eventSettlementService.getSettlementSummary(eventId)).thenReturn(summary);

        ResponseEntity<ApiResponse<EventSummary>> response = adminEventController.getSettlement(eventId);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(summary, response.getBody().getData());
        verify(eventSettlementService, times(1)).getSettlementSummary(eventId);
    }

    @Test
    void testGetSettlement_NotFound() {
        Organizer organizer = new Organizer();
        organizer.setId(UUID.randomUUID());
        mockAdminUser(organizer);

        UUID eventId = UUID.randomUUID();
        Event event = new Event();
        event.setId(eventId);
        event.setOrganizer(organizer);

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(eventSettlementService.getSettlementSummary(eventId)).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () -> adminEventController.getSettlement(eventId));
    }
}

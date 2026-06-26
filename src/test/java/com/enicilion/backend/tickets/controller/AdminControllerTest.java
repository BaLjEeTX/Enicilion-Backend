package com.enicilion.backend.tickets.controller;

import com.enicilion.backend.auth.entity.User;
import com.enicilion.backend.auth.entity.UserRole;
import com.enicilion.backend.auth.repository.UserRepository;
import com.enicilion.backend.common.dto.ApiResponse;
import com.enicilion.backend.payments.entity.Payment;
import com.enicilion.backend.payments.repository.PaymentRepository;
import com.enicilion.backend.tickets.dto.CheckinFeedEventDto;
import com.enicilion.backend.tickets.dto.BoxOfficeRequest;
import com.enicilion.backend.tickets.entity.SpectatorTicket;
import com.enicilion.backend.tickets.repository.TicketRepository;
import com.enicilion.backend.tickets.service.CheckoutService;
import com.enicilion.backend.tickets.service.LiveCheckinService;
import com.enicilion.backend.auth.service.StaffPermissionService;
import com.enicilion.backend.auth.service.SecurityContextService;
import com.enicilion.backend.notification.service.NotificationService;
import com.enicilion.backend.auth.entity.StaffPermission;
import com.enicilion.backend.auth.dto.StaffPermissionRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminControllerTest {

    @Mock
    private CheckoutService checkoutService;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private LiveCheckinService liveCheckinService;
    @Mock
    private StaffPermissionService staffPermissionService;
    @Mock
    private com.enicilion.backend.tickets.service.EmailNotificationService emailNotificationService;
    @Mock
    private NotificationService notificationService;

    @Mock
    private SecurityContext securityContext;
    @Mock
    private Authentication authentication;

    @Mock
    private SecurityContextService securityContextService;

    @InjectMocks
    private AdminController adminController;

    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(securityContextService.getCurrentUser()).thenAnswer(inv -> {
            String email = SecurityContextHolder.getContext().getAuthentication().getName();
            return userRepository.findByEmail(email)
                    .orElseThrow(() -> new com.enicilion.backend.common.exception.UnauthorizedException("Authenticated user not found."));
        });
        doAnswer(inv -> {
            User u = inv.getArgument(0);
            if (u.getRole() != UserRole.admin && u.getRole() != UserRole.staff) {
                throw new com.enicilion.backend.common.exception.UnauthorizedException("Access denied.");
            }
            return null;
        }).when(securityContextService).checkAdminOrStaff(any(User.class));
        doAnswer(inv -> {
            User u = inv.getArgument(0);
            if (u.getRole() != UserRole.admin) {
                throw new com.enicilion.backend.common.exception.UnauthorizedException("Access denied.");
            }
            return null;
        }).when(securityContextService).checkAdminOnly(any(User.class));
    }

    @AfterEach
    void tearDown() throws Exception {
        SecurityContextHolder.clearContext();
        closeable.close();
    }

    @Test
    void testGetTreasuryStatsWithoutSearch() {
        when(authentication.getName()).thenReturn("admin@enicilion.com");
        User adminUser = new User();
        adminUser.setEmail("admin@enicilion.com");
        adminUser.setRole(UserRole.admin);
        when(userRepository.findByEmail("admin@enicilion.com")).thenReturn(Optional.of(adminUser));

        Pageable pageable = PageRequest.of(0, 20);
        Page<Payment> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);
        when(paymentRepository.findAllByOrderByCreatedAtDesc(pageable)).thenReturn(emptyPage);

        ResponseEntity<ApiResponse<Map<String, Object>>> response = adminController.getTreasuryStats(0, 20, null, null);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        verify(paymentRepository, times(1)).findAllByOrderByCreatedAtDesc(pageable);
        verify(paymentRepository, never()).searchTransactions(anyString(), any(Pageable.class));
        verify(staffPermissionService, times(1)).checkPermission(adminUser, "TREASURY");
    }

    @Test
    void testGetTreasuryStatsWithSearch() {
        when(authentication.getName()).thenReturn("admin@enicilion.com");
        User adminUser = new User();
        adminUser.setEmail("admin@enicilion.com");
        adminUser.setRole(UserRole.admin);
        when(userRepository.findByEmail("admin@enicilion.com")).thenReturn(Optional.of(adminUser));

        Pageable pageable = PageRequest.of(0, 20);
        Page<Payment> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);
        when(paymentRepository.searchTransactions("test-query", pageable)).thenReturn(emptyPage);

        ResponseEntity<ApiResponse<Map<String, Object>>> response = adminController.getTreasuryStats(0, 20, "test-query", null);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        verify(paymentRepository, times(1)).searchTransactions("test-query", pageable);
        verify(paymentRepository, never()).findAllByOrderByCreatedAtDesc(any(Pageable.class));
        verify(staffPermissionService, times(1)).checkPermission(adminUser, "TREASURY");
    }

    @Test
    void testGetTreasuryStatsWithEventId() {
        when(authentication.getName()).thenReturn("admin@enicilion.com");
        User adminUser = new User();
        adminUser.setEmail("admin@enicilion.com");
        adminUser.setRole(UserRole.admin);
        when(userRepository.findByEmail("admin@enicilion.com")).thenReturn(Optional.of(adminUser));

        UUID eventId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20);
        Page<Payment> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);
        when(paymentRepository.findAllByEventOrderByCreatedAtDesc(eventId, pageable)).thenReturn(emptyPage);

        ResponseEntity<ApiResponse<Map<String, Object>>> response = adminController.getTreasuryStats(0, 20, null, eventId);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        verify(paymentRepository, times(1)).findAllByEventOrderByCreatedAtDesc(eventId, pageable);
        verify(paymentRepository, times(1)).sumAmountByEventAndStatusAndProvider(eventId, com.enicilion.backend.payments.entity.PaymentStatus.paid, com.enicilion.backend.payments.entity.PaymentProvider.manual);
        verify(paymentRepository, times(1)).countByEventAndStatusAndProvider(eventId, com.enicilion.backend.payments.entity.PaymentStatus.paid, com.enicilion.backend.payments.entity.PaymentProvider.manual);
        verify(paymentRepository, times(1)).sumAmountByEventAndStatusAndProvider(eventId, com.enicilion.backend.payments.entity.PaymentStatus.paid, com.enicilion.backend.payments.entity.PaymentProvider.razorpay);
        verify(paymentRepository, times(1)).countByEventAndStatusAndProvider(eventId, com.enicilion.backend.payments.entity.PaymentStatus.paid, com.enicilion.backend.payments.entity.PaymentProvider.razorpay);
        verify(staffPermissionService, times(1)).checkPermission(adminUser, "TREASURY");
    }

    @Test
    void testGetRecentCheckins() {
        when(authentication.getName()).thenReturn("admin@enicilion.com");
        User adminUser = new User();
        adminUser.setEmail("admin@enicilion.com");
        adminUser.setRole(UserRole.admin);
        when(userRepository.findByEmail("admin@enicilion.com")).thenReturn(Optional.of(adminUser));

        List<CheckinFeedEventDto> mockCheckins = List.of(
            CheckinFeedEventDto.builder().ticketCode("T1").action("scan_success").totalCheckedIn(5).build()
        );
        when(liveCheckinService.getRecentCheckins()).thenReturn(mockCheckins);

        ResponseEntity<ApiResponse<List<CheckinFeedEventDto>>> response = adminController.getRecentCheckins();

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(mockCheckins, response.getBody().getData());
        verify(liveCheckinService, times(1)).getRecentCheckins();
        verify(staffPermissionService, times(1)).checkPermission(adminUser, "LIVE_CHECKIN");
    }

    @Test
    void testGetCheckinStream() {
        when(authentication.getName()).thenReturn("admin@enicilion.com");
        User adminUser = new User();
        adminUser.setEmail("admin@enicilion.com");
        adminUser.setRole(UserRole.admin);
        when(userRepository.findByEmail("admin@enicilion.com")).thenReturn(Optional.of(adminUser));

        SseEmitter mockEmitter = new SseEmitter();
        when(liveCheckinService.subscribe()).thenReturn(mockEmitter);

        SseEmitter response = adminController.getCheckinStream();

        assertNotNull(response);
        assertEquals(mockEmitter, response);
        verify(liveCheckinService, times(1)).subscribe();
        verify(staffPermissionService, times(1)).checkPermission(adminUser, "LIVE_CHECKIN");
    }

    @Test
    void testGetAllStaffPermissions() {
        when(authentication.getName()).thenReturn("admin@enicilion.com");
        User adminUser = User.builder().email("admin@enicilion.com").role(UserRole.admin).build();
        when(userRepository.findByEmail("admin@enicilion.com")).thenReturn(Optional.of(adminUser));

        List<StaffPermission> mockPerms = List.of(
            StaffPermission.builder().email("staff@enicilion.com").feature("TREASURY").build()
        );
        when(staffPermissionService.getAllPermissions()).thenReturn(mockPerms);

        ResponseEntity<ApiResponse<List<StaffPermission>>> response = adminController.getAllStaffPermissions();

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(mockPerms, response.getBody().getData());
    }

    @Test
    void testGrantStaffPermission() {
        when(authentication.getName()).thenReturn("admin@enicilion.com");
        User adminUser = User.builder().email("admin@enicilion.com").role(UserRole.admin).build();
        when(userRepository.findByEmail("admin@enicilion.com")).thenReturn(Optional.of(adminUser));

        StaffPermissionRequest request = new StaffPermissionRequest();
        request.setEmail("staff@enicilion.com");
        request.setFeature("TREASURY");

        StaffPermission mockPerm = StaffPermission.builder().email("staff@enicilion.com").feature("TREASURY").build();
        when(staffPermissionService.grantPermission("staff@enicilion.com", "TREASURY")).thenReturn(mockPerm);

        ResponseEntity<ApiResponse<StaffPermission>> response = adminController.grantStaffPermission(request);

        assertNotNull(response);
        assertEquals(201, response.getStatusCode().value());
        assertEquals(mockPerm, response.getBody().getData());
    }

    @Test
    void testRevokeStaffPermission() {
        when(authentication.getName()).thenReturn("admin@enicilion.com");
        User adminUser = User.builder().email("admin@enicilion.com").role(UserRole.admin).build();
        when(userRepository.findByEmail("admin@enicilion.com")).thenReturn(Optional.of(adminUser));

        ResponseEntity<ApiResponse<Map<String, Object>>> response = adminController.revokeStaffPermission("staff@enicilion.com", "TREASURY");

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("Permission successfully revoked!", response.getBody().getData().get("message"));
        verify(staffPermissionService, times(1)).revokePermission("staff@enicilion.com", "TREASURY");
    }

    @Test
    void testGetMyPermissionsAsAdmin() {
        when(authentication.getName()).thenReturn("admin@enicilion.com");
        User adminUser = User.builder().email("admin@enicilion.com").role(UserRole.admin).build();
        when(userRepository.findByEmail("admin@enicilion.com")).thenReturn(Optional.of(adminUser));

        ResponseEntity<ApiResponse<List<String>>> response = adminController.getMyPermissions();

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().getData().contains("TREASURY"));
        assertTrue(response.getBody().getData().contains("LIVE_CHECKIN"));
        verify(staffPermissionService, never()).getPermissionsByEmail(anyString());
    }

    @Test
    void testGetMyPermissionsAsStaff() {
        when(authentication.getName()).thenReturn("staff@enicilion.com");
        User staffUser = User.builder().email("staff@enicilion.com").role(UserRole.staff).build();
        when(userRepository.findByEmail("staff@enicilion.com")).thenReturn(Optional.of(staffUser));

        StaffPermission perm = StaffPermission.builder().email("staff@enicilion.com").feature("TREASURY").build();
        when(staffPermissionService.getPermissionsByEmail("staff@enicilion.com")).thenReturn(List.of(perm));

        ResponseEntity<ApiResponse<List<String>>> response = adminController.getMyPermissions();

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().getData().size());
        assertEquals("TREASURY", response.getBody().getData().get(0));
        verify(staffPermissionService, times(1)).getPermissionsByEmail("staff@enicilion.com");
    }

    @Test
    void testGetMyPermissionsAsUser() {
        when(authentication.getName()).thenReturn("user@enicilion.com");
        User normalUser = User.builder().email("user@enicilion.com").role(UserRole.user).build();
        when(userRepository.findByEmail("user@enicilion.com")).thenReturn(Optional.of(normalUser));

        assertThrows(com.enicilion.backend.common.exception.UnauthorizedException.class, () -> adminController.getMyPermissions());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testBoxOfficeBuySingleFallback() {
        when(authentication.getName()).thenReturn("admin@enicilion.com");
        User adminUser = User.builder().email("admin@enicilion.com").role(UserRole.admin).build();
        when(userRepository.findByEmail("admin@enicilion.com")).thenReturn(Optional.of(adminUser));

        BoxOfficeRequest request = new BoxOfficeRequest();
        request.setEmail("customer@test.com");
        request.setFullName("Customer Test");
        request.setPhone("1234567890");
        UUID tierId = UUID.randomUUID();
        request.setTierId(tierId);
        request.setQuantity(2);

        SpectatorTicket ticket = new SpectatorTicket();
        ticket.setTicketCode("T123");
        ticket.setStatus(com.enicilion.backend.tickets.entity.TicketStatus.paid);

        when(checkoutService.boxOfficeCheckout(eq("customer@test.com"), eq("Customer Test"), eq("1234567890"), anyList()))
                .thenReturn(List.of(ticket));

        ResponseEntity<ApiResponse<Map<String, Object>>> response = adminController.boxOfficeBuy(request);

        assertNotNull(response);
        assertEquals(201, response.getStatusCode().value());
        Map<String, Object> data = response.getBody().getData();
        assertNotNull(data);
        assertEquals("Tickets successfully issued via Box Office!", data.get("message"));
        List<Map<String, Object>> tickets = (List<Map<String, Object>>) data.get("tickets");
        assertEquals(1, tickets.size());
        assertEquals("T123", tickets.get(0).get("code"));
    }

    @Test
    void testBoxOfficeBuyMulti() {
        when(authentication.getName()).thenReturn("admin@enicilion.com");
        User adminUser = User.builder().email("admin@enicilion.com").role(UserRole.admin).build();
        when(userRepository.findByEmail("admin@enicilion.com")).thenReturn(Optional.of(adminUser));

        BoxOfficeRequest request = new BoxOfficeRequest();
        request.setEmail("customer@test.com");
        request.setFullName("Customer Test");
        request.setPhone("1234567890");

        BoxOfficeRequest.Item item1 = new BoxOfficeRequest.Item();
        item1.setTierId(UUID.randomUUID());
        item1.setQuantity(1);

        BoxOfficeRequest.Item item2 = new BoxOfficeRequest.Item();
        item2.setTierId(UUID.randomUUID());
        item2.setQuantity(3);

        request.setItems(List.of(item1, item2));

        SpectatorTicket ticket = new SpectatorTicket();
        ticket.setTicketCode("T123");
        ticket.setStatus(com.enicilion.backend.tickets.entity.TicketStatus.paid);

        when(checkoutService.boxOfficeCheckout(eq("customer@test.com"), eq("Customer Test"), eq("1234567890"), anyList()))
                .thenReturn(List.of(ticket));

        ResponseEntity<ApiResponse<Map<String, Object>>> response = adminController.boxOfficeBuy(request);

        assertNotNull(response);
        assertEquals(201, response.getStatusCode().value());
        verify(checkoutService, times(1)).boxOfficeCheckout(eq("customer@test.com"), eq("Customer Test"), eq("1234567890"), eq(request.getItems()));
    }

    @Test
    void testSendTicketEmail_Success() {
        when(authentication.getName()).thenReturn("admin@enicilion.com");
        User adminUser = User.builder().email("admin@enicilion.com").role(UserRole.admin).build();
        when(userRepository.findByEmail("admin@enicilion.com")).thenReturn(Optional.of(adminUser));

        String ticketCode = "TC123";
        User buyer = User.builder().email("buyer@test.com").fullName("Buyer Name").isEmailVerified(true).build();
        
        SpectatorTicket ticket = new SpectatorTicket();
        ticket.setTicketCode(ticketCode);
        ticket.setUser(buyer);
        
        when(ticketRepository.findByTicketCodeWithDetails(ticketCode)).thenReturn(Optional.of(ticket));

        ResponseEntity<ApiResponse<Map<String, Object>>> response = adminController.sendTicketEmail(ticketCode);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("Ticket confirmation email successfully sent to buyer@test.com", response.getBody().getData().get("message"));
        
        verify(emailNotificationService, times(1)).sendTicketConfirmationEmail(anyList());
    }

    @Test
    void testSendTicketEmail_UnverifiedUser() {
        when(authentication.getName()).thenReturn("admin@enicilion.com");
        User adminUser = User.builder().email("admin@enicilion.com").role(UserRole.admin).build();
        when(userRepository.findByEmail("admin@enicilion.com")).thenReturn(Optional.of(adminUser));

        String ticketCode = "TC123";
        User buyer = User.builder().email("buyer@test.com").fullName("Buyer Name").isEmailVerified(false).build();
        
        SpectatorTicket ticket = new SpectatorTicket();
        ticket.setTicketCode(ticketCode);
        ticket.setUser(buyer);
        
        when(ticketRepository.findByTicketCodeWithDetails(ticketCode)).thenReturn(Optional.of(ticket));

        assertThrows(com.enicilion.backend.common.exception.BadValidationException.class, 
                () -> adminController.sendTicketEmail(ticketCode));
        
        verify(emailNotificationService, never()).sendTicketConfirmationEmail(anyList());
    }

    @Test
    void testSendTicketEmail_Forbidden() {
        when(authentication.getName()).thenReturn("user@enicilion.com");
        User normalUser = User.builder().email("user@enicilion.com").role(UserRole.user).build();
        when(userRepository.findByEmail("user@enicilion.com")).thenReturn(Optional.of(normalUser));

        assertThrows(com.enicilion.backend.common.exception.UnauthorizedException.class, 
                () -> adminController.sendTicketEmail("TC123"));
        
        verify(emailNotificationService, never()).sendTicketConfirmationEmail(anyList());
    }

    @Test
    void testSendTicketsEmailByUser_Success() {
        when(authentication.getName()).thenReturn("admin@enicilion.com");
        User adminUser = User.builder().email("admin@enicilion.com").role(UserRole.admin).build();
        when(userRepository.findByEmail("admin@enicilion.com")).thenReturn(Optional.of(adminUser));

        String email = "buyer@test.com";
        User buyer = User.builder().id(UUID.randomUUID()).email(email).fullName("Buyer Name").isEmailVerified(true).build();
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(buyer));

        com.enicilion.backend.tickets.entity.Event mockEvent = new com.enicilion.backend.tickets.entity.Event();
        mockEvent.setEventDate(java.time.OffsetDateTime.now().plusDays(10));
        mockEvent.setName("Motorscape Test Event");

        SpectatorTicket ticket = new SpectatorTicket();
        ticket.setTicketCode("TC123");
        ticket.setUser(buyer);
        ticket.setEvent(mockEvent);
        when(ticketRepository.findByUserId(buyer.getId())).thenReturn(List.of(ticket));

        ResponseEntity<ApiResponse<Map<String, Object>>> response = adminController.sendTicketsEmailByUser(email);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("Ticket confirmation email successfully sent to buyer@test.com", response.getBody().getData().get("message"));
        assertEquals(1, response.getBody().getData().get("ticketCount"));
        
        verify(emailNotificationService, times(1)).sendTicketConfirmationEmail(anyList());
    }

    @Test
    void testSendTicketsEmailByUser_UnverifiedUser() {
        when(authentication.getName()).thenReturn("admin@enicilion.com");
        User adminUser = User.builder().email("admin@enicilion.com").role(UserRole.admin).build();
        when(userRepository.findByEmail("admin@enicilion.com")).thenReturn(Optional.of(adminUser));

        String email = "buyer@test.com";
        User buyer = User.builder().id(UUID.randomUUID()).email(email).fullName("Buyer Name").isEmailVerified(false).build();
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(buyer));

        assertThrows(com.enicilion.backend.common.exception.BadValidationException.class, 
                () -> adminController.sendTicketsEmailByUser(email));
        
        verify(emailNotificationService, never()).sendTicketConfirmationEmail(anyList());
    }

    @Test
    void testSendTicketsEmailByUser_NoTickets() {
        when(authentication.getName()).thenReturn("admin@enicilion.com");
        User adminUser = User.builder().email("admin@enicilion.com").role(UserRole.admin).build();
        when(userRepository.findByEmail("admin@enicilion.com")).thenReturn(Optional.of(adminUser));

        String email = "buyer@test.com";
        User buyer = User.builder().id(UUID.randomUUID()).email(email).fullName("Buyer Name").isEmailVerified(true).build();
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(buyer));
        when(ticketRepository.findByUserId(buyer.getId())).thenReturn(Collections.emptyList());

        assertThrows(com.enicilion.backend.common.exception.BadValidationException.class, 
                () -> adminController.sendTicketsEmailByUser(email));
        
        verify(emailNotificationService, never()).sendTicketConfirmationEmail(anyList());
    }

    @Test
    void testSendTicketsEmailByUser_Forbidden() {
        when(authentication.getName()).thenReturn("user@enicilion.com");
        User normalUser = User.builder().email("user@enicilion.com").role(UserRole.user).build();
        when(userRepository.findByEmail("user@enicilion.com")).thenReturn(Optional.of(normalUser));

        assertThrows(com.enicilion.backend.common.exception.UnauthorizedException.class, 
                () -> adminController.sendTicketsEmailByUser("buyer@test.com"));
        
        verify(emailNotificationService, never()).sendTicketConfirmationEmail(anyList());
    }
}

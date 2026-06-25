package com.enicilion.backend.tickets.controller;

import com.enicilion.backend.auth.entity.User;
import com.enicilion.backend.common.exception.UnauthorizedException;
import com.enicilion.backend.tickets.entity.SpectatorTicket;
import com.enicilion.backend.tickets.repository.TicketRepository;
import com.enicilion.backend.tickets.service.PdfService;
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

import com.enicilion.backend.auth.repository.UserRepository;
import com.enicilion.backend.auth.service.SecurityContextService;
import com.enicilion.backend.common.service.SecureTokenService;
import com.enicilion.backend.tickets.service.CheckoutService;
import com.enicilion.backend.tickets.service.ScanService;
import com.enicilion.backend.wallet.service.WalletService;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TicketControllerTest {

    @Mock
    private CheckoutService checkoutService;

    @Mock
    private ScanService scanService;

    @Mock
    private PdfService pdfService;

    @Mock
    private WalletService walletService;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SecureTokenService secureTokenService;

    @Mock
    private SecurityContextService securityContextService;

    @InjectMocks
    private TicketController ticketController;

    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        SecurityContextHolder.clearContext();
        closeable.close();
    }

    private void setAuthenticatedUser(String email) {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn(email);
        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(context);
    }

    @Test
    void testGetTicketPdf_OwnerAccess_Success() {
        setAuthenticatedUser("owner@test.com");

        User owner = User.builder().email("owner@test.com").build();
        SpectatorTicket ticket = SpectatorTicket.builder()
                .ticketCode("TCK123")
                .user(owner)
                .build();

        when(ticketRepository.findByTicketCode("TCK123")).thenReturn(Optional.of(ticket));
        
        List<SpectatorTicket> tickets = new ArrayList<>();
        tickets.add(ticket);
        when(ticketRepository.findByPaymentId(any())).thenReturn(tickets);
        when(pdfService.generateTicketsPdf(anyList())).thenReturn(new byte[]{1, 2, 3});

        ResponseEntity<byte[]> response = ticketController.getTicketPdf("TCK123", null);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertArrayEquals(new byte[]{1, 2, 3}, response.getBody());
    }

    @Test
    void testGetTicketPdf_NonOwnerAccessNoToken_ThrowsUnauthorized() {
        setAuthenticatedUser("nonowner@test.com");

        User owner = User.builder().email("owner@test.com").build();
        SpectatorTicket ticket = SpectatorTicket.builder()
                .ticketCode("TCK123")
                .user(owner)
                .build();

        when(ticketRepository.findByTicketCode("TCK123")).thenReturn(Optional.of(ticket));

        assertThrows(UnauthorizedException.class, () -> ticketController.getTicketPdf("TCK123", null));
    }

    @Test
    void testGetTicketPdf_GuestAccessWithValidToken_Success() {
        setAuthenticatedUser("anonymousUser");

        User owner = User.builder().email("guest@test.com").build();
        SpectatorTicket ticket = SpectatorTicket.builder()
                .ticketCode("TCK123")
                .user(owner)
                .build();

        when(ticketRepository.findByTicketCode("TCK123")).thenReturn(Optional.of(ticket));
        
        List<SpectatorTicket> tickets = new ArrayList<>();
        tickets.add(ticket);
        when(ticketRepository.findByPaymentId(any())).thenReturn(tickets);
        when(pdfService.generateTicketsPdf(anyList())).thenReturn(new byte[]{1, 2, 3});

        // Compute valid token
        String validToken = computeHash("TCK123:guest@test.com:enicilion-secure-salt-2026");
        when(secureTokenService.generateTicketToken(eq("TCK123"), eq("guest@test.com"))).thenReturn(validToken);

        ResponseEntity<byte[]> response = ticketController.getTicketPdf("TCK123", validToken);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertArrayEquals(new byte[]{1, 2, 3}, response.getBody());
    }

    @Test
    void testGetTicketPdf_GuestAccessWithInvalidToken_ThrowsUnauthorized() {
        setAuthenticatedUser("anonymousUser");

        User owner = User.builder().email("guest@test.com").build();
        SpectatorTicket ticket = SpectatorTicket.builder()
                .ticketCode("TCK123")
                .user(owner)
                .build();

        when(ticketRepository.findByTicketCode("TCK123")).thenReturn(Optional.of(ticket));
        when(secureTokenService.generateTicketToken(eq("TCK123"), eq("guest@test.com"))).thenReturn("valid-token");

        assertThrows(UnauthorizedException.class, () -> ticketController.getTicketPdf("TCK123", "invalid-token"));
    }

    private String computeHash(String data) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

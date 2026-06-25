package com.enicilion.backend.tickets.service;

import com.enicilion.backend.auth.entity.User;
import com.enicilion.backend.auth.entity.UserRole;
import com.enicilion.backend.auth.repository.UserRepository;
import com.enicilion.backend.auth.service.ReferralCodeService;
import com.enicilion.backend.common.exception.BadValidationException;
import com.enicilion.backend.influencer.repository.InfluencerEarningsLedgerRepository;
import com.enicilion.backend.payments.entity.Payment;
import com.enicilion.backend.payments.entity.PaymentStatus;
import com.enicilion.backend.common.exception.InventoryExhaustedException;
import com.enicilion.backend.coupons.repository.CouponRepository;
import com.enicilion.backend.coupons.repository.CouponUsageRepository;
import com.enicilion.backend.coupons.service.CouponService;
import com.enicilion.backend.payments.repository.PaymentRepository;
import com.enicilion.backend.tickets.dto.CartItem;
import com.enicilion.backend.tickets.dto.CheckoutRequest;
import com.enicilion.backend.tickets.dto.PaymentResponse;
import com.enicilion.backend.tickets.entity.Event;
import com.enicilion.backend.tickets.entity.TicketTier;
import com.enicilion.backend.tickets.repository.TicketRepository;
import com.enicilion.backend.tickets.repository.TicketTierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CheckoutServiceTest {

    @Mock
    private TicketTierRepository tierRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private CouponService couponService;

    @Mock
    private CouponUsageRepository couponUsageRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private InfluencerEarningsLedgerRepository influencerEarningsLedgerRepository;

    @Mock
    private RedisInventoryService redisInventoryService;

    @Mock
    private ReferralCodeService referralCodeService;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private CheckoutService checkoutService;

    private UUID tierId;
    private UUID eventId;
    private User testUser;
    private Event testEvent;
    private TicketTier testTier;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        tierId = UUID.randomUUID();
        eventId = UUID.randomUUID();

        testUser = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .fullName("Test User")
                .role(UserRole.user)
                .referralCode("TESTU1234")
                .build();

        testEvent = new Event();
        testEvent.setId(eventId);
        testEvent.setName("Motorscape 2026");

        testTier = new TicketTier();
        testTier.setId(tierId);
        testTier.setName("General Admission");
        testTier.setPrice(new BigDecimal("500"));
        testTier.setQuantity(100);
        testTier.setEvent(testEvent);
    }

    @Test
    void checkout_happyPath_returnsPaymentResponse() {
        // Arrange
        CheckoutRequest request = createCheckoutRequest(1, null);

        when(redisInventoryService.tryReserveInventory(tierId, 1)).thenReturn(true);
        when(tierRepository.findByIdForUpdate(tierId)).thenReturn(Optional.of(testTier));
        when(paymentRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            p.setCurrency("INR");
            return p;
        });

        // Act
        PaymentResponse response = checkoutService.checkout(request, testUser, null);

        // Assert
        assertNotNull(response);
        assertNotNull(response.getPaymentId());
        // 500 (tier price) + 49 (service fee for 1 access ticket) = 549
        assertEquals(new BigDecimal("549"), response.getAmount());

        verify(paymentRepository, times(2)).save(any(Payment.class));
        verify(ticketRepository).saveAll(anyList());
    }

    @Test
    void checkout_idempotencyDuplicate_returnsExistingPayment() {
        // Arrange
        String idempotencyKey = "idem-key-123";
        CheckoutRequest request = createCheckoutRequest(1, null);
        request.setIdempotencyKey(idempotencyKey);

        UUID existingPaymentId = UUID.randomUUID();
        Payment existingPayment = Payment.builder()
                .id(existingPaymentId)
                .amount(new BigDecimal("549"))
                .currency("INR")
                .status(PaymentStatus.pending)
                .build();

        when(paymentRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existingPayment));

        // Act
        PaymentResponse response = checkoutService.checkout(request, testUser, null);

        // Assert
        assertEquals(existingPaymentId.toString(), response.getPaymentId());
        assertEquals(new BigDecimal("549"), response.getAmount());
        assertEquals("INR", response.getCurrency());

        // No new payment should be created
        verify(paymentRepository, never()).save(any(Payment.class));
        verify(ticketRepository, never()).saveAll(anyList());
    }

    @Test
    void checkout_guestUser_createsNewUser() {
        // Arrange
        CheckoutRequest request = createCheckoutRequest(1, null);
        request.setGuestEmail("newguest@example.com");
        request.setGuestName("New Guest");

        when(userRepository.findByEmail("newguest@example.com")).thenReturn(Optional.empty());
        when(referralCodeService.generateUniqueReferralCode("New Guest")).thenReturn("NEWGUE1234");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });
        when(redisInventoryService.tryReserveInventory(tierId, 1)).thenReturn(true);
        when(tierRepository.findByIdForUpdate(tierId)).thenReturn(Optional.of(testTier));
        when(paymentRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            p.setCurrency("INR");
            return p;
        });

        // Act — pass null user to trigger guest flow
        PaymentResponse response = checkoutService.checkout(request, null, null);

        // Assert
        assertNotNull(response);
        verify(userRepository).findByEmail("newguest@example.com");
        verify(referralCodeService).generateUniqueReferralCode("New Guest");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void checkout_inventoryExhausted_throwsException() {
        // Arrange
        CheckoutRequest request = createCheckoutRequest(1, null);

        when(redisInventoryService.tryReserveInventory(tierId, 1)).thenReturn(false);
        when(paymentRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(InventoryExhaustedException.class, () ->
                checkoutService.checkout(request, testUser, null));

        // No payment or tickets should be created
        verify(paymentRepository, never()).save(any(Payment.class));
        verify(ticketRepository, never()).saveAll(anyList());
    }

    @Test
    void checkout_bulkDiscount_appliesTenPercentOff() {
        // Arrange — 5 tickets triggers 10% bulk discount
        CheckoutRequest request = createCheckoutRequest(5, null);

        when(redisInventoryService.tryReserveInventory(tierId, 5)).thenReturn(true);
        when(tierRepository.findByIdForUpdate(tierId)).thenReturn(Optional.of(testTier));
        when(paymentRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            p.setCurrency("INR");
            return p;
        });

        // Act
        PaymentResponse response = checkoutService.checkout(request, testUser, null);

        // Assert
        assertNotNull(response);
        // 5 * 500 = 2500 base, 10% off = 250 discount → 2250 + (5*49 service) = 2250 + 245 = 2495
        assertEquals(new BigDecimal("2495"), response.getAmount());
    }

    @Test
    void checkout_missingIdempotencyKey_throwsValidationError() {
        // Arrange — no idempotency key provided
        CheckoutRequest request = createCheckoutRequest(1, null);
        request.setIdempotencyKey(null);

        // Act & Assert
        assertThrows(BadValidationException.class, () ->
                checkoutService.checkout(request, testUser, null));
    }

    @Test
    void checkout_missingGuestEmail_throwsValidationError() {
        // Arrange — null user with no guest email
        CheckoutRequest request = createCheckoutRequest(1, null);
        request.setGuestEmail(null);

        // Act & Assert
        assertThrows(BadValidationException.class, () ->
                checkoutService.checkout(request, null, null));
    }

    // --- Helper Methods ---

    private CheckoutRequest createCheckoutRequest(int quantity, String couponCode) {
        CartItem cartItem = new CartItem();
        cartItem.setEventId(eventId);
        cartItem.setTierId(tierId);
        cartItem.setQuantity(quantity);

        CheckoutRequest request = new CheckoutRequest();
        request.setItems(List.of(cartItem));
        request.setCouponCode(couponCode);
        request.setIdempotencyKey("test-idempotency-" + UUID.randomUUID());
        return request;
    }
}

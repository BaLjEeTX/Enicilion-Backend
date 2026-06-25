package com.enicilion.backend.payments.service;

import com.enicilion.backend.payments.entity.Payment;
import com.enicilion.backend.payments.entity.PaymentStatus;
import com.enicilion.backend.payments.entity.PaymentProvider;
import com.enicilion.backend.payments.repository.PaymentRepository;
import com.enicilion.backend.tickets.entity.SpectatorTicket;
import com.enicilion.backend.tickets.entity.TicketStatus;
import com.enicilion.backend.tickets.repository.TicketRepository;
import com.enicilion.backend.tickets.service.RedisInventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RazorpayServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private RedisInventoryService redisInventoryService;

    @InjectMocks
    private RazorpayService razorpayService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testVerifyAndProcessPayment_Failure_ReturnsFailedStatus() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = Payment.builder()
                .id(paymentId)
                .amount(new java.math.BigDecimal("100"))
                .status(PaymentStatus.pending)
                .provider(PaymentProvider.razorpay)
                .build();

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        // Force signature verification fail by using non-matching parameters
        // and bypass disabled
        
        SpectatorTicket ticket = new SpectatorTicket();
        ticket.setStatus(TicketStatus.booked);
        List<SpectatorTicket> tickets = List.of(ticket);
        when(ticketRepository.findByPaymentId(paymentId)).thenReturn(tickets);

        Map<String, Object> result = razorpayService.verifyAndProcessPayment(
                paymentId, "order_123", "pay_123", "invalid_signature");

        assertNotNull(result);
        assertEquals("failed", result.get("status"));
        assertEquals(PaymentStatus.failed, payment.getStatus());
        assertEquals(TicketStatus.cancelled, ticket.getStatus());

        verify(paymentRepository, times(1)).save(payment);
        verify(ticketRepository, times(1)).saveAll(anyList());
    }
}

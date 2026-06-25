package com.enicilion.backend.tickets.service;

import com.enicilion.backend.tickets.entity.TicketStatus;
import com.enicilion.backend.tickets.entity.TicketTier;
import com.enicilion.backend.tickets.repository.TicketRepository;
import com.enicilion.backend.tickets.repository.TicketTierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RedisInventoryServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private TicketTierRepository ticketTierRepository;

    @Mock
    private TicketRepository ticketRepository;

    @InjectMocks
    private RedisInventoryService redisInventoryService;

    private UUID tierId;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        tierId = UUID.randomUUID();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testTryReserveInventorySuccess() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(1L);

        boolean result = redisInventoryService.tryReserveInventory(tierId, 2);

        assertTrue(result);
        verify(redisTemplate, times(1)).execute(any(RedisScript.class), eq(Collections.singletonList("ticket_tier:" + tierId + ":available")), eq("2"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testTryReserveInventorySoldOut() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(0L);

        boolean result = redisInventoryService.tryReserveInventory(tierId, 2);

        assertFalse(result);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testTryReserveInventoryInitializationRequired() {
        // Return -1 on first execution (initialize key), -1 on double-check in lock, then 1 on retry
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any()))
                .thenReturn(-1L)
                .thenReturn(-1L)
                .thenReturn(1L);

        TicketTier tier = TicketTier.builder()
                .id(tierId)
                .name("GA")
                .price(BigDecimal.valueOf(100.0))
                .quantity(100)
                .build();
        when(ticketTierRepository.findById(tierId)).thenReturn(Optional.of(tier));

        when(ticketRepository.countByTierIdAndStatusIn(eq(tierId), anyList())).thenReturn(20);
        when(valueOperations.setIfAbsent(anyString(), anyString())).thenReturn(true);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(java.time.Duration.class))).thenReturn(true);

        boolean result = redisInventoryService.tryReserveInventory(tierId, 2);

        assertTrue(result);
        verify(ticketTierRepository, times(1)).findById(tierId);
        verify(ticketRepository, times(1)).countByTierIdAndStatusIn(eq(tierId), anyList());
        verify(valueOperations, times(1)).setIfAbsent(eq("ticket_tier:" + tierId + ":available"), eq("80"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testReleaseInventory() {
        redisInventoryService.releaseInventory(tierId, 2);

        verify(redisTemplate, times(1)).execute(any(RedisScript.class), eq(Collections.singletonList("ticket_tier:" + tierId + ":available")), eq("2"));
    }
}

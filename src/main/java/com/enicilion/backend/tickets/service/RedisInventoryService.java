package com.enicilion.backend.tickets.service;

import com.enicilion.backend.tickets.entity.TicketStatus;
import com.enicilion.backend.tickets.entity.TicketTier;
import com.enicilion.backend.tickets.repository.TicketRepository;
import com.enicilion.backend.tickets.repository.TicketTierRepository;
import com.enicilion.backend.common.exception.BadValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisInventoryService {

    private final StringRedisTemplate redisTemplate;
    private final TicketTierRepository ticketTierRepository;
    private final TicketRepository ticketRepository;

    private static final String RESERVE_SCRIPT =
            "local key = KEYS[1]\n" +
            "local qty = tonumber(ARGV[1])\n" +
            "local current = redis.call('get', key)\n" +
            "if not current then\n" +
            "    return -1\n" +
            "end\n" +
            "local current_num = tonumber(current)\n" +
            "if current_num >= qty then\n" +
            "    redis.call('decrby', key, qty)\n" +
            "    return 1\n" +
            "else\n" +
            "    return 0\n" +
            "end";

    private static final String RELEASE_SCRIPT =
            "local key = KEYS[1]\n" +
            "local qty = tonumber(ARGV[1])\n" +
            "if redis.call('exists', key) == 1 then\n" +
            "    redis.call('incrby', key, qty)\n" +
            "    return 1\n" +
            "else\n" +
            "    return 0\n" +
            "end";

    private final RedisScript<Long> reserveScript = new DefaultRedisScript<>(RESERVE_SCRIPT, Long.class);
    private final RedisScript<Long> releaseScript = new DefaultRedisScript<>(RELEASE_SCRIPT, Long.class);

    /**
     * Tries to reserve inventory for a ticket tier.
     * Thread-safe and atomic. Uses a Redis lock to prevent concurrent
     * initialization race conditions on cold start.
     *
     * @param tierId   the ticket tier ID
     * @param quantity the quantity to reserve
     * @return true if reserved successfully, false if sold out
     */
    public boolean tryReserveInventory(UUID tierId, int quantity) {
        String key = getRedisKey(tierId);
        
        // Execute Lua reservation script
        Long result = redisTemplate.execute(reserveScript, Collections.singletonList(key), String.valueOf(quantity));
        
        if (result == null) {
            return false;
        }

        if (result == -1) {
            // Key is not initialized in Redis. Use a lock to prevent concurrent initialization.
            String lockKey = key + ":init_lock";
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", java.time.Duration.ofSeconds(5));
            
            if (Boolean.TRUE.equals(acquired)) {
                try {
                    // Double-check: another thread may have initialized while we waited for the lock
                    result = redisTemplate.execute(reserveScript, Collections.singletonList(key), String.valueOf(quantity));
                    if (result != null && result != -1) {
                        return result == 1;
                    }
                    // Still missing — we are the initializer
                    initializeInventory(tierId);
                } finally {
                    redisTemplate.delete(lockKey);
                }
            } else {
                // Another thread is initializing — wait briefly and retry
                try { Thread.sleep(50); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            }
            
            // Retry the reservation after initialization
            result = redisTemplate.execute(reserveScript, Collections.singletonList(key), String.valueOf(quantity));
        }

        return result != null && result == 1;
    }

    /**
     * Releases (returns) inventory back to the ticket tier in Redis.
     * Only increments if the key exists to avoid dirty initialization.
     *
     * @param tierId   the ticket tier ID
     * @param quantity the quantity to release
     */
    public void releaseInventory(UUID tierId, int quantity) {
        String key = getRedisKey(tierId);
        redisTemplate.execute(releaseScript, Collections.singletonList(key), String.valueOf(quantity));
    }

    /**
     * Synchronizes a ticket tier's capacity back to Redis.
     * Useful if an admin changes ticket quantities.
     */
    public void syncInventory(UUID tierId) {
        String key = getRedisKey(tierId);
        int available = calculateAvailableCapacity(tierId);
        redisTemplate.opsForValue().set(key, String.valueOf(available));
        log.info("Synchronized Redis inventory for tier {}: available = {}", tierId, available);
    }

    private void initializeInventory(UUID tierId) {
        String key = getRedisKey(tierId);
        int available = calculateAvailableCapacity(tierId);
        // Set only if key does not exist (atomic)
        redisTemplate.opsForValue().setIfAbsent(key, String.valueOf(available));
        log.info("Initialized Redis inventory for tier {}: available = {}", tierId, available);
    }

    private int calculateAvailableCapacity(UUID tierId) {
        TicketTier tier = ticketTierRepository.findById(tierId)
                .orElseThrow(() -> new BadValidationException("Ticket tier not found: " + tierId));

        if (tier.getQuantity() == null) {
            // Unlimited tickets tier, initialize with a very large number in Redis
            return 999999;
        }

        // Active tickets are those that are booked or paid
        int activeTickets = ticketRepository.countByTierIdAndStatusIn(
                tierId,
                List.of(TicketStatus.booked, TicketStatus.paid)
        );

        return Math.max(0, tier.getQuantity() - activeTickets);
    }

    public void deleteInventory(UUID tierId) {
        String key = getRedisKey(tierId);
        redisTemplate.delete(key);
        log.info("Deleted Redis inventory key for tier {}", tierId);
    }

    private String getRedisKey(UUID tierId) {
        return "ticket_tier:" + tierId + ":available";
    }
}

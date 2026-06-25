package com.enicilion.backend.auth.service;

import com.enicilion.backend.auth.entity.User;
import com.enicilion.backend.auth.entity.UserRole;
import com.enicilion.backend.auth.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Caches user lookups in Redis to avoid hitting PostgreSQL on every authenticated request.
 * The JWT filter calls findByEmailCached() instead of the repository directly.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserCacheService {

    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String KEY_PREFIX = "user:cache:";
    private static final Duration CACHE_TTL = Duration.ofSeconds(60);

    /**
     * Lightweight DTO stored in Redis — avoids serializing full JPA entity with lazy proxies.
     */
    public record CachedUser(
            String id,
            String email,
            String fullName,
            String role,
            boolean banned
    ) {}

    /**
     * Looks up a user by email, checking Redis first. On miss, queries the database
     * and populates the cache.
     */
    public Optional<User> findByEmailCached(String email) {
        String key = KEY_PREFIX + email.toLowerCase();

        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json != null) {
                CachedUser cached = objectMapper.readValue(json, CachedUser.class);
                // Reconstruct a minimal User object from cache — sufficient for SecurityContext
                User user = new User();
                user.setId(UUID.fromString(cached.id()));
                user.setEmail(cached.email());
                user.setFullName(cached.fullName());
                user.setRole(UserRole.valueOf(cached.role()));
                user.setBanned(cached.banned());
                return Optional.of(user);
            }
        } catch (Exception e) {
            log.debug("Redis cache miss or error for user lookup key={}: {}", key, e.getMessage());
        }

        // Cache miss — fall through to database
        Optional<User> userOpt = userRepository.findByEmail(email);
        userOpt.ifPresent(user -> cacheUser(key, user));
        return userOpt;
    }

    /**
     * Evicts a user from the cache. Call this when a user's role or ban status changes.
     */
    public void evictUserCache(String email) {
        String key = KEY_PREFIX + email.toLowerCase();
        redisTemplate.delete(key);
        log.debug("Evicted user cache for email={}", email);
    }

    private void cacheUser(String key, User user) {
        try {
            CachedUser cached = new CachedUser(
                    user.getId().toString(),
                    user.getEmail(),
                    user.getFullName(),
                    user.getRole().name(),
                    user.isBanned()
            );
            String json = objectMapper.writeValueAsString(cached);
            redisTemplate.opsForValue().set(key, json, CACHE_TTL);
        } catch (JsonProcessingException e) {
            log.warn("Failed to cache user {}: {}", user.getEmail(), e.getMessage());
        }
    }
}

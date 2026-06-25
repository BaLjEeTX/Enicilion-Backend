package com.enicilion.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

/**
 * A lightweight, Redis-backed rate limiting filter to protect sensitive public routes.
 * Rate limits requests by IP address on a per-minute sliding window.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitingFilter extends OncePerRequestFilter {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${app.rate-limit.max-requests:20}")
    private int maxRequests;

    @Value("${app.rate-limit.enabled:true}")
    private boolean enabled;

    private static final List<String> LIMITED_PATHS = List.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/tickets/checkout",
            "/api/coupons/validate"
    );

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }

        String uri = request.getRequestURI();
        boolean isLimited = LIMITED_PATHS.stream().anyMatch(uri::equalsIgnoreCase);

        if (isLimited) {
            String ip = getClientIp(request);
            long currentMinute = System.currentTimeMillis() / 60000;
            String key = "rate:limit:" + ip + ":" + uri + ":" + currentMinute;

            try {
                Long count = redisTemplate.opsForValue().increment(key);
                if (count != null) {
                    if (count == 1) {
                        redisTemplate.expire(key, Duration.ofSeconds(65)); // Expiry slightly longer than 1 minute
                    }
                    if (count > maxRequests) {
                        log.warn("[SECURITY_ALERT] [RATE_LIMIT_BLOCKED] Rate limit exceeded for IP={} on URI={}. Requests={} (Limit={})", ip, uri, count, maxRequests);
                        response.setStatus(429); // Too Many Requests
                        response.setContentType("application/json");
                        response.setCharacterEncoding("UTF-8");
                        response.getWriter().write("{\"success\":false,\"message\":\"Too many requests. Please try again in a minute.\"}");
                        return;
                    }
                }
            } catch (Exception e) {
                // Fail-safe: log error but allow request to proceed if Redis is down
                log.error("Error checking rate limit in Redis for key={}", key, e);
            }
        }

        filterChain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isEmpty()) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }
}

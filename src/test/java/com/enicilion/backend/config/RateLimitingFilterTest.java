package com.enicilion.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class RateLimitingFilterTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private RateLimitingFilter rateLimitingFilter;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        ReflectionTestUtils.setField(rateLimitingFilter, "maxRequests", 2);
        ReflectionTestUtils.setField(rateLimitingFilter, "enabled", true);
    }

    @Test
    void testFilter_NonLimitedPath_PassesThrough() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/public/events");

        rateLimitingFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void testFilter_LimitedPath_UnderLimit_PassesThrough() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/auth/login");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(valueOperations.increment(anyString())).thenReturn(1L);

        rateLimitingFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
        verify(valueOperations, times(1)).increment(anyString());
        verify(redisTemplate, times(1)).expire(anyString(), any(Duration.class));
    }

    @Test
    void testFilter_LimitedPath_OverLimit_Blocked() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/auth/login");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(valueOperations.increment(anyString())).thenReturn(3L); // Max is 2

        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(printWriter);

        rateLimitingFilter.doFilterInternal(request, response, filterChain);

        verify(response, times(1)).setStatus(429);
        verify(response, times(1)).setContentType("application/json");
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void testFilter_Disabled_PassesThrough() throws Exception {
        ReflectionTestUtils.setField(rateLimitingFilter, "enabled", false);
        when(request.getRequestURI()).thenReturn("/api/auth/login");

        rateLimitingFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
        verifyNoInteractions(redisTemplate);
    }
}

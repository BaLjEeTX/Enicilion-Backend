package com.enicilion.backend.config;

import com.enicilion.backend.auth.entity.User;
import com.enicilion.backend.auth.service.JwtService;
import com.enicilion.backend.auth.service.UserCacheService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserCacheService userCacheService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        String jwt = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            jwt = authHeader.substring(7);
        }
        final String userEmail;

        log.debug("JwtAuthenticationFilter: Incoming request to URI={} with Authorization header={}", 
                request.getRequestURI(), authHeader != null ? "present" : "null");

        if (jwt == null) {
            log.debug("JwtAuthenticationFilter: No Bearer token in Authorization header");
            filterChain.doFilter(request, response);
            return;
        }
        try {
            boolean isValid = jwtService.isAccessTokenValid(jwt);
            log.debug("JwtAuthenticationFilter: Access token valid? {}", isValid);
            
            if (isValid) {
                userEmail = jwtService.extractEmailFromAccessToken(jwt);
                log.debug("JwtAuthenticationFilter: Extracted email={}", userEmail);
                
                if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    User user = userCacheService.findByEmailCached(userEmail).orElse(null);
                    log.debug("JwtAuthenticationFilter: User found? {}", user != null);
                    
                    if (user != null && !user.isBanned()) {
                        String roleName = user.getRole().name();
                        // Spring Security expects roles to match: e.g. ROLE_admin or ROLE_ADMIN
                        SimpleGrantedAuthority authorityLower = new SimpleGrantedAuthority("ROLE_" + roleName.toLowerCase());
                        SimpleGrantedAuthority authorityUpper = new SimpleGrantedAuthority("ROLE_" + roleName.toUpperCase());
                        
                        log.debug("JwtAuthenticationFilter: Authenticated user={} with role={}", 
                                userEmail, roleName);
                        
                        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                userEmail,
                                null,
                                java.util.List.of(authorityLower, authorityUpper)
                        );
                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                    } else if (user != null) {
                        log.warn("JwtAuthenticationFilter: User is banned: {}", userEmail);
                    }
                }
            } else {
                log.warn("JwtAuthenticationFilter: Invalid JWT token signature or token expired");
            }
        } catch (Exception e) {
            log.error("Failed to set user authentication in security context", e);
        }

        filterChain.doFilter(request, response);
    }
}

package com.enicilion.backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final RateLimitingFilter rateLimitingFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(org.springframework.security.config.Customizer.withDefaults())
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public routes
                .requestMatchers("/", "/index.html", "/favicon.ico", "/verify-email.html", "/static/**", "/uploads/**").permitAll()
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/events", "/api/events/**").permitAll()
                .requestMatchers("/api/coupons/validate").permitAll()
                .requestMatchers("/api/support/tickets").permitAll()
                .requestMatchers("/api/tickets/scan", "/api/tickets/revert-checkin").permitAll() // Internal scanner password verification
                .requestMatchers("/api/tickets/pdf/**", "/api/tickets/payment/*/pdf").permitAll() // Public PDF rendering endpoint
                .requestMatchers("/api/tickets/*/apple-wallet", "/api/tickets/*/google-wallet").permitAll() // Public Wallet pass endpoints
                .requestMatchers("/api/tickets/checkout").permitAll() // Public for Guest Checkout
                .requestMatchers("/api/payments/razorpay/verify").permitAll() // Webhook/callback public endpoint
                .requestMatchers("/api/ticket-pdf/**").permitAll() // Public raw ticket PDF generation for Meta WA
                
                // Secure routes
                .requestMatchers("/api/admin/**").hasAnyRole("admin", "staff", "ADMIN", "STAFF")
                .requestMatchers("/api/applications/**").authenticated()
                
                // All other requests require authentication
                .anyRequest().authenticated()
            )
            .addFilterBefore(rateLimitingFilter, org.springframework.security.web.context.SecurityContextHolderFilter.class)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12); // BCrypt with 12 rounds
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}

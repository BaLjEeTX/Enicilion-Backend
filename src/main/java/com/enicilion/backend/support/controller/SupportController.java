package com.enicilion.backend.support.controller;

import com.enicilion.backend.auth.entity.User;
import com.enicilion.backend.auth.repository.UserRepository;
import com.enicilion.backend.common.dto.ApiResponse;
import com.enicilion.backend.support.dto.SupportTicketRequest;
import com.enicilion.backend.support.entity.SupportTicket;
import com.enicilion.backend.support.service.SupportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/support")
@RequiredArgsConstructor
public class SupportController {

    private final SupportService supportService;
    private final UserRepository userRepository;

    private UUID resolveCurrentUserId() {
        try {
            String email = SecurityContextHolder.getContext().getAuthentication().getName();
            if (email != null && !email.equals("anonymousUser")) {
                return userRepository.findByEmail(email)
                        .map(User::getId)
                        .orElse(null);
            }
        } catch (Exception e) {
            // Context not set or unauthenticated
        }
        return null;
    }

    @PostMapping("/tickets")
    public ResponseEntity<ApiResponse<Map<String, Object>>> submitTicket(
            @Valid @RequestBody SupportTicketRequest request) {
        
        UUID userId = resolveCurrentUserId();
        
        SupportTicket ticket = supportService.createSupportTicket(
                request.getCategory(),
                request.getMessage(),
                request.getName(),
                request.getPhone(),
                request.getEmail(),
                userId
        );

        Map<String, Object> data = new HashMap<>();
        data.put("id", ticket.getId().toString());
        data.put("status", ticket.getStatus());
        data.put("createdAt", ticket.getCreatedAt());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(data));
    }
}

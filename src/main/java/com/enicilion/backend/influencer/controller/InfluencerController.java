package com.enicilion.backend.influencer.controller;

import com.enicilion.backend.auth.entity.User;
import com.enicilion.backend.auth.entity.UserRole;
import com.enicilion.backend.auth.repository.UserRepository;
import com.enicilion.backend.common.dto.ApiResponse;
import com.enicilion.backend.common.exception.UnauthorizedException;
import com.enicilion.backend.influencer.dto.InfluencerApplyRequest;
import com.enicilion.backend.influencer.entity.InfluencerApplication;
import com.enicilion.backend.influencer.service.InfluencerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/influencer")
@RequiredArgsConstructor
public class InfluencerController {

    private final InfluencerService influencerService;
    private final UserRepository userRepository;

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Authenticated user not found."));
    }

    @PostMapping("/apply")
    public ResponseEntity<ApiResponse<InfluencerApplication>> apply(
            @Valid @RequestBody InfluencerApplyRequest request) {
        User user = getCurrentUser();
        InfluencerApplication app = influencerService.apply(request, user);
        return ResponseEntity.ok(ApiResponse.success(app));
    }

    @GetMapping("/application/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getApplicationStatus() {
        User user = getCurrentUser();
        Map<String, Object> status = influencerService.getApplicationStatus(user);
        return ResponseEntity.ok(ApiResponse.success(status));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDashboard() {
        User user = getCurrentUser();
        if (user.getRole() != UserRole.influencer && user.getRole() != UserRole.admin) {
            throw new UnauthorizedException("Access denied. Influencer account required.");
        }
        Map<String, Object> dashboard = influencerService.getDashboard(user);
        return ResponseEntity.ok(ApiResponse.success(dashboard));
    }
}

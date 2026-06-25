package com.enicilion.backend.applications.controller;

import com.enicilion.backend.applications.dto.ReviewApplicationRequest;
import com.enicilion.backend.applications.entity.EventApplication;
import com.enicilion.backend.applications.service.ApplicationService;
import com.enicilion.backend.auth.entity.User;
import com.enicilion.backend.auth.entity.UserRole;
import com.enicilion.backend.auth.repository.UserRepository;
import com.enicilion.backend.common.dto.ApiResponse;
import com.enicilion.backend.common.exception.UnauthorizedException;
import com.enicilion.backend.auth.service.StaffPermissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;
    private final UserRepository userRepository;
    private final StaffPermissionService staffPermissionService;

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Authenticated user not found."));
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<Map<String, Object>>> submitApplication(
            @RequestParam("eventId") String eventId,
            @RequestParam("responses") String responsesJson,
            @RequestParam(value = "photos", required = false) List<MultipartFile> photos) {

        User user = getCurrentUser();
        UUID eventUuid = UUID.fromString(eventId);

        EventApplication application = applicationService.submitApplication(
                eventUuid,
                user,
                responsesJson,
                photos
        );

        Map<String, Object> data = new HashMap<>();
        data.put("id", application.getId().toString());
        data.put("status", application.getStatus().name());
        data.put("submittedAt", application.getCreatedAt());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(data));
    }

    @PostMapping("/{id}/review")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reviewApplication(
            @PathVariable("id") String id,
            @Valid @RequestBody ReviewApplicationRequest request) {

        User reviewer = getCurrentUser();
        staffPermissionService.checkPermission(reviewer, "DRIFTER_REVIEW");

        UUID appUuid = UUID.fromString(id);
        EventApplication application = applicationService.reviewApplication(
                appUuid,
                request.getStatus(),
                request.getAdminNotes(),
                reviewer
        );

        Map<String, Object> data = new HashMap<>();
        data.put("id", application.getId().toString());
        data.put("status", application.getStatus().name());
        data.put("reviewedAt", application.getReviewedAt());

        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAllApplications() {
        User reviewer = getCurrentUser();
        staffPermissionService.checkPermission(reviewer, "DRIFTER_REVIEW");

        List<Map<String, Object>> responseList = applicationService.getAllApplicationsMapped();
        return ResponseEntity.ok(ApiResponse.success(responseList));
    }
}

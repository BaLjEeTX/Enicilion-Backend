package com.enicilion.backend.influencer.controller;

import com.enicilion.backend.auth.entity.User;
import com.enicilion.backend.auth.entity.UserRole;
import com.enicilion.backend.auth.repository.UserRepository;
import com.enicilion.backend.common.dto.ApiResponse;
import com.enicilion.backend.common.exception.UnauthorizedException;
import com.enicilion.backend.auth.service.StaffPermissionService;
import com.enicilion.backend.coupons.entity.Coupon;
import com.enicilion.backend.influencer.dto.*;
import com.enicilion.backend.influencer.entity.InfluencerApplication;
import com.enicilion.backend.influencer.entity.InfluencerAuditLog;
import com.enicilion.backend.influencer.entity.InfluencerPayout;
import com.enicilion.backend.influencer.entity.InfluencerProfile;
import com.enicilion.backend.influencer.service.InfluencerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/influencers")
@RequiredArgsConstructor
public class InfluencerAdminController {

    private final InfluencerService influencerService;
    private final UserRepository userRepository;
    private final StaffPermissionService staffPermissionService;

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Authenticated user not found."));
    }

    private void checkAdminOrStaff(User user) {
        staffPermissionService.checkPermission(user, "INFLUENCER_MGMT");
    }

    @GetMapping("/applications")
    public ResponseEntity<ApiResponse<List<InfluencerApplication>>> getApplications() {
        User user = getCurrentUser();
        checkAdminOrStaff(user);
        List<InfluencerApplication> list = influencerService.adminGetApplications();
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @PostMapping("/applications/{id}/status")
    public ResponseEntity<ApiResponse<InfluencerApplication>> reviewApplication(
            @PathVariable("id") UUID id,
            @Valid @RequestBody InfluencerApplicationActionRequest request) {
        User user = getCurrentUser();
        checkAdminOrStaff(user);
        InfluencerApplication app = influencerService.adminReviewApplication(id, request, user.getEmail());
        return ResponseEntity.ok(ApiResponse.success(app));
    }

    @GetMapping("/profiles")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getProfiles() {
        User user = getCurrentUser();
        checkAdminOrStaff(user);
        List<Map<String, Object>> list = influencerService.adminGetProfiles();
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @PostMapping("/profiles/{id}/commission")
    public ResponseEntity<ApiResponse<InfluencerProfile>> updateCommission(
            @PathVariable("id") UUID id,
            @Valid @RequestBody CommissionUpdateRequest request) {
        User user = getCurrentUser();
        checkAdminOrStaff(user);
        InfluencerProfile profile = influencerService.adminUpdateCommission(id, request, user.getEmail());
        return ResponseEntity.ok(ApiResponse.success(profile));
    }

    @GetMapping("/coupons")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getCoupons() {
        User user = getCurrentUser();
        checkAdminOrStaff(user);
        List<Map<String, Object>> list = influencerService.adminGetCoupons();
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @PostMapping("/coupons")
    public ResponseEntity<ApiResponse<Coupon>> createCoupon(
            @Valid @RequestBody InfluencerCouponCreateRequest request) {
        User user = getCurrentUser();
        checkAdminOrStaff(user);
        Coupon coupon = influencerService.adminCreateCoupon(request, user.getEmail());
        return ResponseEntity.ok(ApiResponse.success(coupon));
    }

    @GetMapping("/payouts")
    public ResponseEntity<ApiResponse<List<InfluencerPayout>>> getPayouts() {
        User user = getCurrentUser();
        checkAdminOrStaff(user);
        List<InfluencerPayout> list = influencerService.adminGetPayouts();
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @PostMapping("/payouts")
    public ResponseEntity<ApiResponse<InfluencerPayout>> triggerPayout(
            @RequestParam("profileId") UUID profileId) {
        User user = getCurrentUser();
        checkAdminOrStaff(user);
        InfluencerPayout payout = influencerService.adminTriggerPayout(profileId, user.getEmail());
        return ResponseEntity.ok(ApiResponse.success(payout));
    }

    @GetMapping("/analytics")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAnalytics() {
        User user = getCurrentUser();
        checkAdminOrStaff(user);
        Map<String, Object> analytics = influencerService.adminGetAnalytics();
        return ResponseEntity.ok(ApiResponse.success(analytics));
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<ApiResponse<List<InfluencerAuditLog>>> getAuditLogs() {
        User user = getCurrentUser();
        checkAdminOrStaff(user);
        List<InfluencerAuditLog> list = influencerService.adminGetAuditLogs();
        return ResponseEntity.ok(ApiResponse.success(list));
    }
}

package com.enicilion.backend.influencer.service;

import com.enicilion.backend.auth.entity.User;
import com.enicilion.backend.auth.entity.UserRole;
import com.enicilion.backend.auth.repository.UserRepository;
import com.enicilion.backend.common.exception.BadValidationException;
import com.enicilion.backend.common.exception.ResourceNotFoundException;
import com.enicilion.backend.coupons.entity.Coupon;
import com.enicilion.backend.coupons.repository.CouponRepository;
import com.enicilion.backend.influencer.dto.*;
import com.enicilion.backend.influencer.entity.*;
import com.enicilion.backend.influencer.repository.*;
import com.enicilion.backend.tickets.entity.Event;
import com.enicilion.backend.tickets.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InfluencerService {

    private final UserRepository userRepository;
    private final InfluencerApplicationRepository applicationRepository;
    private final InfluencerProfileRepository profileRepository;
    private final InfluencerPayoutRepository payoutRepository;
    private final InfluencerEarningsLedgerRepository ledgerRepository;
    private final InfluencerAuditLogRepository auditLogRepository;
    private final CouponRepository couponRepository;
    private final EventRepository eventRepository;

    @Transactional
    public InfluencerApplication apply(InfluencerApplyRequest request, User user) {
        // Check if user already has a pending or approved application
        Optional<InfluencerApplication> existingOpt = applicationRepository.findByUserId(user.getId());
        if (existingOpt.isPresent()) {
            InfluencerApplication existing = existingOpt.get();
            if (existing.getStatus() == ApplicationStatus.APPROVED) {
                throw new BadValidationException("You are already an approved influencer.");
            }
            if (existing.getStatus() == ApplicationStatus.PENDING) {
                throw new BadValidationException("Your application is already pending review.");
            }
            // If REJECTED or SUSPENDED, allow resubmitting
            existing.setFullName(request.getFullName());
            existing.setEmail(request.getEmail());
            existing.setPhone(request.getPhone());
            existing.setSocialLinks(request.getSocialLinks());
            existing.setFollowerCount(request.getFollowerCount());
            existing.setNicheDescription(request.getNicheDescription());
            existing.setPaymentDetails(request.getPaymentDetails());
            existing.setStatus(ApplicationStatus.PENDING);
            existing.setUpdatedAt(OffsetDateTime.now());
            return applicationRepository.save(existing);
        }

        InfluencerApplication application = InfluencerApplication.builder()
                .user(user)
                .fullName(request.getFullName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .socialLinks(request.getSocialLinks())
                .followerCount(request.getFollowerCount())
                .nicheDescription(request.getNicheDescription())
                .paymentDetails(request.getPaymentDetails())
                .status(ApplicationStatus.PENDING)
                .build();

        return applicationRepository.save(application);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getApplicationStatus(User user) {
        Optional<InfluencerApplication> appOpt = applicationRepository.findByUserId(user.getId());
        Map<String, Object> response = new HashMap<>();
        if (appOpt.isPresent()) {
            InfluencerApplication app = appOpt.get();
            response.put("hasApplied", true);
            response.put("status", app.getStatus().name());
            response.put("notes", app.getNotes());
            response.put("appliedAt", app.getCreatedAt());
        } else {
            response.put("hasApplied", false);
            response.put("status", "NONE");
        }
        return response;
    }

    @Transactional(readOnly = true)
    public List<InfluencerApplication> adminGetApplications() {
        return applicationRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public InfluencerApplication adminReviewApplication(UUID applicationId, InfluencerApplicationActionRequest request, String actorEmail) {
        InfluencerApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Influencer application not found."));

        ApplicationStatus oldStatus = app.getStatus();
        app.setStatus(request.getStatus());
        app.setNotes(request.getNotes());
        app.setUpdatedAt(OffsetDateTime.now());
        applicationRepository.save(app);

        User user = app.getUser();

        if (request.getStatus() == ApplicationStatus.APPROVED) {
            // Upgrade role
            user.setRole(UserRole.influencer);
            user.setUpdatedAt(OffsetDateTime.now());
            userRepository.save(user);

            // Fetch or create profile
            InfluencerProfile profile = profileRepository.findByUserId(user.getId())
                    .orElseGet(() -> {
                        InfluencerProfile newProfile = InfluencerProfile.builder()
                                .user(user)
                                .commissionType(request.getCommissionType() != null ? request.getCommissionType() : CommissionType.percentage)
                                .commissionValue(request.getCommissionValue() != null ? request.getCommissionValue() : BigDecimal.valueOf(10.00))
                                .build();
                        return profileRepository.save(newProfile);
                    });

            // Update profile rules if set in request
            if (request.getCommissionType() != null) {
                profile.setCommissionType(request.getCommissionType());
            }
            if (request.getCommissionValue() != null) {
                profile.setCommissionValue(request.getCommissionValue());
            }
            profile.setUpdatedAt(OffsetDateTime.now());
            profileRepository.save(profile);

            // Generate influencer coupon if requested
            String code = request.getCouponCode() != null ? request.getCouponCode().trim().toUpperCase() : "";
            if (!code.isEmpty()) {
                Optional<Coupon> existingCoupon = couponRepository.findByCode(code);
                if (existingCoupon.isPresent()) {
                    Coupon coupon = existingCoupon.get();
                    // Link profile
                    coupon.setInfluencerCoupon(true);
                    coupon.setInfluencerProfile(profile);
                    coupon.setDiscountPercentage(request.getDiscountPercentage() != null ? request.getDiscountPercentage() : 10);
                    coupon.setActive(true);
                    couponRepository.save(coupon);
                } else {
                    Coupon coupon = Coupon.builder()
                            .code(code)
                            .discountPercentage(request.getDiscountPercentage() != null ? request.getDiscountPercentage() : 10)
                            .maxUses(999999)
                            .usedCount(0)
                            .isActive(true)
                            .isInfluencerCoupon(true)
                            .influencerProfile(profile)
                            .build();
                    couponRepository.save(coupon);
                }
            }

            logAudit(
                "APPLICATION_APPROVED",
                actorEmail,
                app.getId(),
                "Onboarding approved for user: " + user.getEmail() + ". Role updated to influencer. Coupon set: " + code
            );

        } else if (request.getStatus() == ApplicationStatus.SUSPENDED || request.getStatus() == ApplicationStatus.REJECTED) {
            // Revoke role
            user.setRole(UserRole.user);
            user.setUpdatedAt(OffsetDateTime.now());
            userRepository.save(user);

            // Deactivate influencer coupons if profile exists
            profileRepository.findByUserId(user.getId()).ifPresent(profile -> {
                List<Coupon> coupons = couponRepository.findAll().stream()
                        .filter(c -> c.getInfluencerProfile() != null && c.getInfluencerProfile().getId().equals(profile.getId()))
                        .collect(Collectors.toList());
                for (Coupon c : coupons) {
                    c.setActive(false);
                    c.setUpdatedAt(OffsetDateTime.now());
                    couponRepository.save(c);
                }
            });

            logAudit(
                request.getStatus() == ApplicationStatus.SUSPENDED ? "INFLUENCER_SUSPENDED" : "APPLICATION_REJECTED",
                actorEmail,
                app.getId(),
                "Status changed to " + request.getStatus() + " for applicant user: " + user.getEmail() + ". Role demoted to user."
            );
        }

        return app;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getDashboard(User user) {
        InfluencerProfile profile = profileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Influencer profile not found. Verify application status first."));

        // Query ledger entries
        List<InfluencerEarningsLedger> ledger = ledgerRepository.findByInfluencerProfileId(profile.getId());

        // Get matching coupons
        List<Coupon> coupons = couponRepository.findAll().stream()
                .filter(c -> c.getInfluencerProfile() != null && c.getInfluencerProfile().getId().equals(profile.getId()))
                .collect(Collectors.toList());

        BigDecimal pendingEarnings = BigDecimal.ZERO;
        BigDecimal approvedEarnings = BigDecimal.ZERO;
        BigDecimal paidEarnings = BigDecimal.ZERO;

        int totalSalesCount = 0;
        BigDecimal totalRevenueGenerated = BigDecimal.ZERO;

        for (InfluencerEarningsLedger entry : ledger) {
            if (entry.getStatus() == LedgerStatus.pending) {
                pendingEarnings = pendingEarnings.add(entry.getAmount());
            } else if (entry.getStatus() == LedgerStatus.approved) {
                approvedEarnings = approvedEarnings.add(entry.getAmount());
                totalSalesCount++;
                // Add ticket paid amount to revenue
                if (entry.getTicket() != null) {
                    BigDecimal tierPrice = entry.getTicket().getTier() != null ? entry.getTicket().getTier().getPrice() : BigDecimal.ZERO;
                    BigDecimal discount = BigDecimal.valueOf(entry.getTicket().getDiscountApplied());
                    totalRevenueGenerated = totalRevenueGenerated.add(tierPrice.subtract(discount));
                }
            } else if (entry.getStatus() == LedgerStatus.paid) {
                paidEarnings = paidEarnings.add(entry.getAmount());
                totalSalesCount++;
                if (entry.getTicket() != null) {
                    BigDecimal tierPrice = entry.getTicket().getTier() != null ? entry.getTicket().getTier().getPrice() : BigDecimal.ZERO;
                    BigDecimal discount = BigDecimal.valueOf(entry.getTicket().getDiscountApplied());
                    totalRevenueGenerated = totalRevenueGenerated.add(tierPrice.subtract(discount));
                }
            }
        }

        // Payout history
        List<InfluencerPayout> payouts = payoutRepository.findByInfluencerProfileIdOrderByCreatedAtDesc(profile.getId());

        Map<String, Object> response = new HashMap<>();
        response.put("profileId", profile.getId().toString());
        response.put("commissionType", profile.getCommissionType().name());
        response.put("commissionValue", profile.getCommissionValue());
        
        List<Map<String, Object>> couponList = new ArrayList<>();
        for (Coupon c : coupons) {
            Map<String, Object> cMap = new HashMap<>();
            cMap.put("code", c.getCode());
            cMap.put("discountPercentage", c.getDiscountPercentage());
            cMap.put("isActive", c.isActive());
            couponList.add(cMap);
        }
        response.put("coupons", couponList);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalTicketsSold", totalSalesCount);
        stats.put("totalRevenueGenerated", totalRevenueGenerated);
        stats.put("pendingEarnings", pendingEarnings);
        stats.put("approvedEarnings", approvedEarnings);
        stats.put("paidEarnings", paidEarnings);
        stats.put("totalEarnings", approvedEarnings.add(paidEarnings));
        response.put("stats", stats);

        List<Map<String, Object>> payoutList = new ArrayList<>();
        for (InfluencerPayout p : payouts) {
            Map<String, Object> pMap = new HashMap<>();
            pMap.put("id", p.getId().toString());
            pMap.put("amount", p.getAmount());
            pMap.put("status", p.getStatus().name());
            pMap.put("requestedAt", p.getCreatedAt());
            pMap.put("paidAt", p.getPaidAt());
            payoutList.add(pMap);
        }
        response.put("payouts", payoutList);

        return response;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> adminGetProfiles() {
        List<InfluencerProfile> profiles = profileRepository.findAll();
        List<Map<String, Object>> result = new ArrayList<>();
        
        for (InfluencerProfile p : profiles) {
            Map<String, Object> pMap = new HashMap<>();
            pMap.put("profileId", p.getId().toString());
            pMap.put("fullName", p.getUser().getFullName());
            pMap.put("email", p.getUser().getEmail());
            pMap.put("commissionType", p.getCommissionType().name());
            pMap.put("commissionValue", p.getCommissionValue());
            
            // Calculate ledger metrics
            List<InfluencerEarningsLedger> ledger = ledgerRepository.findByInfluencerProfileId(p.getId());
            BigDecimal pending = BigDecimal.ZERO;
            BigDecimal approved = BigDecimal.ZERO;
            BigDecimal paid = BigDecimal.ZERO;
            int totalSales = 0;
            BigDecimal totalRevenue = BigDecimal.ZERO;

            for (InfluencerEarningsLedger entry : ledger) {
                if (entry.getStatus() == LedgerStatus.pending) {
                    pending = pending.add(entry.getAmount());
                } else if (entry.getStatus() == LedgerStatus.approved) {
                    approved = approved.add(entry.getAmount());
                    totalSales++;
                    if (entry.getTicket() != null) {
                        BigDecimal tierPrice = entry.getTicket().getTier() != null ? entry.getTicket().getTier().getPrice() : BigDecimal.ZERO;
                        BigDecimal discount = BigDecimal.valueOf(entry.getTicket().getDiscountApplied());
                        totalRevenue = totalRevenue.add(tierPrice.subtract(discount));
                    }
                } else if (entry.getStatus() == LedgerStatus.paid) {
                    paid = paid.add(entry.getAmount());
                    totalSales++;
                    if (entry.getTicket() != null) {
                        BigDecimal tierPrice = entry.getTicket().getTier() != null ? entry.getTicket().getTier().getPrice() : BigDecimal.ZERO;
                        BigDecimal discount = BigDecimal.valueOf(entry.getTicket().getDiscountApplied());
                        totalRevenue = totalRevenue.add(tierPrice.subtract(discount));
                    }
                }
            }

            pMap.put("pendingEarnings", pending);
            pMap.put("approvedEarnings", approved);
            pMap.put("paidEarnings", paid);
            pMap.put("totalSales", totalSales);
            pMap.put("totalRevenue", totalRevenue);
            
            result.add(pMap);
        }
        return result;
    }

    @Transactional
    public InfluencerProfile adminUpdateCommission(UUID profileId, CommissionUpdateRequest request, String actorEmail) {
        InfluencerProfile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new ResourceNotFoundException("Influencer profile not found."));

        CommissionType oldType = profile.getCommissionType();
        BigDecimal oldValue = profile.getCommissionValue();

        profile.setCommissionType(request.getCommissionType());
        profile.setCommissionValue(request.getCommissionValue());
        profile.setUpdatedAt(OffsetDateTime.now());
        profileRepository.save(profile);

        logAudit(
            "COMMISSION_UPDATED",
            actorEmail,
            profile.getId(),
            "Commission updated for " + profile.getUser().getEmail() + ". " +
            "Old: " + oldType + " " + oldValue + ", New: " + request.getCommissionType() + " " + request.getCommissionValue()
        );

        return profile;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> adminGetCoupons() {
        List<Coupon> coupons = couponRepository.findAll().stream()
                .filter(Coupon::isInfluencerCoupon)
                .collect(Collectors.toList());

        List<Map<String, Object>> result = new ArrayList<>();
        for (Coupon c : coupons) {
            Map<String, Object> cMap = new HashMap<>();
            cMap.put("id", c.getId().toString());
            cMap.put("code", c.getCode());
            cMap.put("discountPercentage", c.getDiscountPercentage());
            cMap.put("isActive", c.isActive());
            cMap.put("maxUses", c.getMaxUses());
            cMap.put("usedCount", c.getUsedCount());
            cMap.put("validFrom", c.getValidFrom());
            cMap.put("validUntil", c.getValidUntil());
            
            if (c.getInfluencerProfile() != null) {
                cMap.put("influencerProfileId", c.getInfluencerProfile().getId().toString());
                cMap.put("influencerName", c.getInfluencerProfile().getUser().getFullName());
                cMap.put("influencerEmail", c.getInfluencerProfile().getUser().getEmail());
            }
            if (c.getApplicableEvent() != null) {
                cMap.put("eventId", c.getApplicableEvent().getId().toString());
                cMap.put("eventName", c.getApplicableEvent().getName());
            }
            result.add(cMap);
        }
        return result;
    }

    @Transactional
    public Coupon adminCreateCoupon(InfluencerCouponCreateRequest request, String actorEmail) {
        InfluencerProfile profile = profileRepository.findById(request.getInfluencerProfileId())
                .orElseThrow(() -> new ResourceNotFoundException("Influencer profile not found."));

        String code = request.getCode().trim().toUpperCase();
        if (couponRepository.findByCode(code).isPresent()) {
            throw new BadValidationException("Coupon code already exists.");
        }

        Event event = null;
        if (request.getApplicableEventId() != null) {
            event = eventRepository.findById(request.getApplicableEventId())
                    .orElseThrow(() -> new ResourceNotFoundException("Event not found."));
        }

        Coupon coupon = Coupon.builder()
                .code(code)
                .discountPercentage(request.getDiscountPercentage())
                .validFrom(request.getValidFrom())
                .validUntil(request.getValidUntil())
                .maxUses(request.getMaxUses() != null ? request.getMaxUses() : 999999)
                .usedCount(0)
                .isActive(true)
                .isInfluencerCoupon(true)
                .influencerProfile(profile)
                .applicableEvent(event)
                .build();

        coupon = couponRepository.save(coupon);

        logAudit(
            "COUPON_CREATED",
            actorEmail,
            coupon.getId(),
            "Created influencer coupon " + code + " for influencer: " + profile.getUser().getEmail() + " (discount: " + request.getDiscountPercentage() + "%)"
        );

        return coupon;
    }

    @Transactional(readOnly = true)
    public List<InfluencerPayout> adminGetPayouts() {
        return payoutRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public InfluencerPayout adminTriggerPayout(UUID profileId, String actorEmail) {
        InfluencerProfile profile = profileRepository.findByIdForUpdate(profileId)
                .orElseThrow(() -> new ResourceNotFoundException("Influencer profile not found."));

        // Query ledger entries with status = approved
        List<InfluencerEarningsLedger> approvedLedger = ledgerRepository.findByInfluencerProfileIdAndStatus(profile.getId(), LedgerStatus.approved);
        if (approvedLedger.isEmpty()) {
            throw new BadValidationException("Influencer has no approved earnings eligible for payout.");
        }

        BigDecimal payoutAmount = BigDecimal.ZERO;
        for (InfluencerEarningsLedger entry : approvedLedger) {
            payoutAmount = payoutAmount.add(entry.getAmount());
        }

        // Create payout
        InfluencerPayout payout = InfluencerPayout.builder()
                .influencerProfile(profile)
                .amount(payoutAmount)
                .status(PayoutStatus.completed)
                .paidAt(OffsetDateTime.now())
                .build();
        payout = payoutRepository.save(payout);

        // Update ledger records
        for (InfluencerEarningsLedger entry : approvedLedger) {
            entry.setStatus(LedgerStatus.paid);
            entry.setPayout(payout);
            entry.setUpdatedAt(OffsetDateTime.now());
            ledgerRepository.save(entry);
        }

        logAudit(
            "PAYOUT_COMPLETED",
            actorEmail,
            payout.getId(),
            "Completed payout of " + payoutAmount + " for influencer: " + profile.getUser().getEmail()
        );

        return payout;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> adminGetAnalytics() {
        List<InfluencerProfile> profiles = profileRepository.findAll();
        List<Map<String, Object>> topInfluencers = new ArrayList<>();

        for (InfluencerProfile p : profiles) {
            List<InfluencerEarningsLedger> ledger = ledgerRepository.findByInfluencerProfileId(p.getId());
            int ticketsSold = 0;
            BigDecimal revenue = BigDecimal.ZERO;
            BigDecimal commission = BigDecimal.ZERO;

            for (InfluencerEarningsLedger entry : ledger) {
                if (entry.getStatus() == LedgerStatus.approved || entry.getStatus() == LedgerStatus.paid) {
                    ticketsSold++;
                    commission = commission.add(entry.getAmount());
                    if (entry.getTicket() != null) {
                        BigDecimal tierPrice = entry.getTicket().getTier() != null ? entry.getTicket().getTier().getPrice() : BigDecimal.ZERO;
                        BigDecimal discount = BigDecimal.valueOf(entry.getTicket().getDiscountApplied());
                        revenue = revenue.add(tierPrice.subtract(discount));
                    }
                }
            }

            Map<String, Object> iMap = new HashMap<>();
            iMap.put("fullName", p.getUser().getFullName());
            iMap.put("email", p.getUser().getEmail());
            iMap.put("ticketsSold", ticketsSold);
            iMap.put("revenueGenerated", revenue);
            iMap.put("commissionEarned", commission);
            topInfluencers.add(iMap);
        }

        // Sort by tickets sold descending
        topInfluencers.sort((a, b) -> Integer.compare((int) b.get("ticketsSold"), (int) a.get("ticketsSold")));

        // Date-wise sales aggregation
        List<InfluencerEarningsLedger> allLedger = ledgerRepository.findAll();
        Map<String, Map<String, Object>> dailyAggregation = new TreeMap<>(Collections.reverseOrder()); // sorted desc by date
        DateTimeFormatter dayFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        for (InfluencerEarningsLedger entry : allLedger) {
            if (entry.getStatus() == LedgerStatus.approved || entry.getStatus() == LedgerStatus.paid) {
                String day = entry.getCreatedAt().format(dayFormatter);
                dailyAggregation.putIfAbsent(day, new HashMap<String, Object>() {{
                    put("date", day);
                    put("ticketsSold", 0);
                    put("revenue", BigDecimal.ZERO);
                    put("commission", BigDecimal.ZERO);
                }});

                Map<String, Object> dayStats = dailyAggregation.get(day);
                dayStats.put("ticketsSold", (int) dayStats.get("ticketsSold") + 1);
                dayStats.put("commission", ((BigDecimal) dayStats.get("commission")).add(entry.getAmount()));
                
                if (entry.getTicket() != null) {
                    BigDecimal tierPrice = entry.getTicket().getTier() != null ? entry.getTicket().getTier().getPrice() : BigDecimal.ZERO;
                    BigDecimal discount = BigDecimal.valueOf(entry.getTicket().getDiscountApplied());
                    dayStats.put("revenue", ((BigDecimal) dayStats.get("revenue")).add(tierPrice.subtract(discount)));
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("topInfluencers", topInfluencers);
        result.put("dateWiseSales", new ArrayList<>(dailyAggregation.values()));
        return result;
    }

    @Transactional(readOnly = true)
    public List<InfluencerAuditLog> adminGetAuditLogs() {
        return auditLogRepository.findAllByOrderByCreatedAtDesc();
    }

    private void logAudit(String action, String actorEmail, UUID targetId, String details) {
        InfluencerAuditLog logEntry = InfluencerAuditLog.builder()
                .action(action)
                .actorEmail(actorEmail)
                .targetId(targetId)
                .details(details)
                .build();
        auditLogRepository.save(logEntry);
    }
}

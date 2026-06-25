package com.enicilion.backend.tickets.service;

import com.enicilion.backend.common.exception.UnauthorizedException;
import com.enicilion.backend.tickets.dto.ScanResponse;
import com.enicilion.backend.tickets.entity.*;
import com.enicilion.backend.tickets.repository.CheckinRepository;
import com.enicilion.backend.tickets.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScanService {

    private final TicketRepository ticketRepository;
    private final CheckinRepository checkinRepository;
    private final FirebaseSyncService firebaseSyncService;
    private final LiveCheckinService liveCheckinService;

    @Value("${app.scanner.password}")
    private String scannerPassword;

    @Transactional
    public ScanResponse scanTicket(String code, String password) {
        String cleanCode = code != null ? code.trim() : "";

        // 1. Password Verification (Bypass if request is authenticated as admin or staff)
        boolean isAuthenticatedStaff = false;
        try {
            org.springframework.security.core.Authentication auth = 
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser")) {
                isAuthenticatedStaff = auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_admin") || a.getAuthority().equals("ROLE_staff") ||
                                   a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_STAFF"));
            }
        } catch (Exception e) {
            // Ignore
        }

        if (!isAuthenticatedStaff) {
            if (scannerPassword == null || password == null || 
                !java.security.MessageDigest.isEqual(
                    scannerPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8), 
                    password.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
                throw new UnauthorizedException("Unauthorized: Invalid scanner password");
            }
        }

        // 2. Fetch Ticket
        SpectatorTicket ticket = ticketRepository.findByTicketCodeForUpdate(cleanCode)
                .orElse(null);
        if (ticket == null) {
            return new ScanResponse(false, "❌ Invalid ticket", null);
        }

        // 3. Check Cancelled State
        if (ticket.getStatus() == TicketStatus.cancelled) {
            return new ScanResponse(false, "❌ Ticket is blocked/cancelled", null);
        }

        // 5. Count Total Failed Attempts
        // (Note: To prevent blocking valid tickets on first scan, we count attempts to double-scan/scan-invalid-status)
        int attemptsCount = checkinRepository.countByTicketCodeAndAction(cleanCode, "scan_attempt");
        if (attemptsCount >= 3) {
            ticket.setStatus(TicketStatus.cancelled);
            ticket.setUpdatedAt(OffsetDateTime.now());
            ticketRepository.save(ticket);

            // Sync Block state to Firebase (runs asynchronously outside the current HTTP thread)
            firebaseSyncService.syncBlockState(ticket.getTicketCode(), true, attemptsCount);
            return new ScanResponse(false, "❌ Ticket blocked: scanned 3 times", null);
        }

        // 6. Handle Duplicate Scan
        if (ticket.getStatus() == TicketStatus.checked_in) {
            // Check for operator double scan (within 15 seconds grace period)
            boolean isRecentCheckin = ticket.getCheckedInAt() != null && 
                    ticket.getCheckedInAt().isAfter(OffsetDateTime.now().minusSeconds(15));
            
            if (isRecentCheckin) {
                // Record as a double_scan event so it doesn't count towards the 3 scan attempts limit
                recordCheckinAttempt(ticket, "double_scan", "recent duplicate scan (double scan)");
                String holderName = ticket.getUser() != null ? ticket.getUser().getFullName() : null;
                String tierName = ticket.getTier() != null ? ticket.getTier().getName() : "General Admission";
                return new ScanResponse(false, "⚠️ Already scanned! (Double Scan)", holderName, tierName);
            }

            // Record failed checkin attempt
            recordCheckinAttempt(ticket, "scan_attempt", "duplicate scan attempt");
            int newAttemptsCount = attemptsCount + 1;
            
            // Check if this duplicate scan breaches the security threshold
            if (newAttemptsCount >= 3) {
                ticket.setStatus(TicketStatus.cancelled);
                ticket.setUpdatedAt(OffsetDateTime.now());
                ticketRepository.save(ticket);
                firebaseSyncService.syncBlockState(ticket.getTicketCode(), true, newAttemptsCount);
                return new ScanResponse(false, "❌ Ticket blocked: scanned 3 times", null);
            }
            
            String holderName = ticket.getUser() != null ? ticket.getUser().getFullName() : null;
            String tierName = ticket.getTier() != null ? ticket.getTier().getName() : "General Admission";
            return new ScanResponse(false, "⚠️ Already scanned!", holderName, tierName);
        }

        // 7. Validate Paid Status
        if (ticket.getStatus() != TicketStatus.paid) {
            recordCheckinAttempt(ticket, "scan_attempt", "scan attempt on unpaid ticket");
            return new ScanResponse(false, "❌ Ticket status: " + ticket.getStatus() + ". Only paid tickets can be scanned.", null);
        }

        // 8. Successful Scan
        ticket.setStatus(TicketStatus.checked_in);
        ticket.setCheckedInAt(OffsetDateTime.now());
        ticket.setUpdatedAt(OffsetDateTime.now());
        ticketRepository.save(ticket);

        // Record successful checkin event
        recordCheckinAttempt(ticket, "scan_success", "ticket validation success");

        // Sync Check-in state to Firebase (runs asynchronously outside the current HTTP thread)
        firebaseSyncService.syncCheckInState(ticket.getTicketCode(), ticket.getCheckedInAt(), attemptsCount);

        String tierName = ticket.getTier() != null ? ticket.getTier().getName() : "General Admission";
        return new ScanResponse(true, "✅ Valid! Welcome in.", ticket.getUser().getFullName(), tierName);
    }

    private void recordCheckinAttempt(SpectatorTicket ticket, String action, String reason) {
        CheckinEvent attempt = CheckinEvent.builder()
                .ticketId(ticket.getId())
                .ticketCode(ticket.getTicketCode())
                .action(action)
                .gate("gate_api")
                .operatorId("scanner_terminal")
                .reason(reason)
                .build();
        attempt = checkinRepository.save(attempt);
        
        int totalCheckedIn = ticketRepository.countByStatusIn(java.util.List.of(TicketStatus.checked_in));
        liveCheckinService.publishCheckin(attempt, ticket, totalCheckedIn);
    }

    @Transactional
    public ScanResponse revertCheckin(String code, String password) {
        String cleanCode = code != null ? code.trim() : "";

        // 1. Password Verification (Bypass if request is authenticated as admin or staff)
        boolean isAuthenticatedStaff = false;
        try {
            org.springframework.security.core.Authentication auth = 
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser")) {
                isAuthenticatedStaff = auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_admin") || a.getAuthority().equals("ROLE_staff") ||
                                   a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_STAFF"));
            }
        } catch (Exception e) {
            // Ignore
        }

        if (!isAuthenticatedStaff) {
            if (scannerPassword == null || password == null || 
                !java.security.MessageDigest.isEqual(
                    scannerPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8), 
                    password.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
                throw new UnauthorizedException("Unauthorized: Invalid scanner password");
            }
        }

        // 2. Fetch Ticket
        SpectatorTicket ticket = ticketRepository.findByTicketCodeForUpdate(cleanCode)
                .orElse(null);
        if (ticket == null) {
            return new ScanResponse(false, "❌ Invalid ticket", null);
        }

        // 3. Verify status is checked_in
        if (ticket.getStatus() != TicketStatus.checked_in) {
            return new ScanResponse(false, "❌ Ticket status: " + ticket.getStatus() + ". Only checked in tickets can be reverted.", null);
        }

        // 4. Revert Status
        ticket.setStatus(TicketStatus.paid);
        ticket.setCheckedInAt(null);
        ticket.setUpdatedAt(OffsetDateTime.now());
        ticketRepository.save(ticket);

        // Record successful revert event
        recordCheckinAttempt(ticket, "revert_success", "ticket checkin revert");

        // Sync Reverted state to Firebase
        int attemptsCount = checkinRepository.countByTicketCodeAndAction(cleanCode, "scan_attempt");
        firebaseSyncService.syncRevertCheckInState(ticket.getTicketCode(), attemptsCount);

        String holderName = ticket.getUser() != null ? ticket.getUser().getFullName() : null;
        String tierName = ticket.getTier() != null ? ticket.getTier().getName() : "General Admission";
        return new ScanResponse(true, "✅ Reverted! Check-in undone.", holderName, tierName);
    }
}

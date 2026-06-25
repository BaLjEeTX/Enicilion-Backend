package com.enicilion.backend.tickets.controller;

import com.enicilion.backend.auth.entity.User;
import com.enicilion.backend.tickets.dto.CheckinFeedEventDto;
import com.enicilion.backend.tickets.service.LiveCheckinService;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.enicilion.backend.auth.service.StaffPermissionService;
import com.enicilion.backend.auth.service.SecurityContextService;
import com.enicilion.backend.auth.entity.StaffPermission;
import com.enicilion.backend.auth.dto.StaffPermissionRequest;
import com.enicilion.backend.auth.entity.UserRole;
import com.enicilion.backend.auth.repository.UserRepository;
import com.enicilion.backend.common.dto.ApiResponse;
import com.enicilion.backend.common.exception.BadValidationException;
import com.enicilion.backend.common.exception.ResourceNotFoundException;
import com.enicilion.backend.common.exception.UnauthorizedException;
import com.enicilion.backend.payments.entity.Payment;
import com.enicilion.backend.payments.entity.PaymentProvider;
import com.enicilion.backend.payments.entity.PaymentStatus;
import com.enicilion.backend.payments.repository.PaymentRepository;
import com.enicilion.backend.tickets.dto.BoxOfficeRequest;
import com.enicilion.backend.tickets.entity.SpectatorTicket;
import com.enicilion.backend.tickets.service.CheckoutService;
import com.enicilion.backend.tickets.service.EmailNotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.enicilion.backend.tickets.repository.TicketRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import java.time.OffsetDateTime;
import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final CheckoutService checkoutService;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final TicketRepository ticketRepository;
    private final LiveCheckinService liveCheckinService;
    private final StaffPermissionService staffPermissionService;
    private final EmailNotificationService emailNotificationService;
    private final SecurityContextService securityContextService;



    @GetMapping("/treasury")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTreasuryStats(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "eventId", required = false) UUID eventId) {
        User user = securityContextService.getCurrentUser();
        staffPermissionService.checkPermission(user, "TREASURY");

        BigDecimal manualTotal;
        long manualCount;
        BigDecimal razorpayTotal;
        long razorpayCount;
        Page<Payment> paymentPage;

        Pageable pageable = PageRequest.of(page, size);

        if (eventId != null) {
            manualTotal = paymentRepository.sumAmountByEventAndStatusAndProvider(eventId, PaymentStatus.paid, PaymentProvider.manual);
            manualCount = paymentRepository.countByEventAndStatusAndProvider(eventId, PaymentStatus.paid, PaymentProvider.manual);
            razorpayTotal = paymentRepository.sumAmountByEventAndStatusAndProvider(eventId, PaymentStatus.paid, PaymentProvider.razorpay);
            razorpayCount = paymentRepository.countByEventAndStatusAndProvider(eventId, PaymentStatus.paid, PaymentProvider.razorpay);

            if (search != null && !search.trim().isEmpty()) {
                paymentPage = paymentRepository.searchTransactionsByEvent(eventId, search.trim(), pageable);
            } else {
                paymentPage = paymentRepository.findAllByEventOrderByCreatedAtDesc(eventId, pageable);
            }
        } else {
            manualTotal = paymentRepository.sumAmountByStatusAndProvider(PaymentStatus.paid, PaymentProvider.manual);
            manualCount = paymentRepository.countByStatusAndProvider(PaymentStatus.paid, PaymentProvider.manual);
            razorpayTotal = paymentRepository.sumAmountByStatusAndProvider(PaymentStatus.paid, PaymentProvider.razorpay);
            razorpayCount = paymentRepository.countByStatusAndProvider(PaymentStatus.paid, PaymentProvider.razorpay);

            if (search != null && !search.trim().isEmpty()) {
                paymentPage = paymentRepository.searchTransactions(search.trim(), pageable);
            } else {
                paymentPage = paymentRepository.findAllByOrderByCreatedAtDesc(pageable);
            }
        }

        List<Map<String, Object>> txList = new ArrayList<>();
        for (Payment p : paymentPage.getContent()) {
            Map<String, Object> pMap = new HashMap<>();
            pMap.put("id", p.getId().toString());
            pMap.put("amount", p.getAmount());
            pMap.put("provider", p.getProvider().name());
            pMap.put("status", p.getStatus().name());
            pMap.put("createdAt", p.getCreatedAt());
            pMap.put("paidAt", p.getPaidAt());

            if (p.getUser() != null) {
                Map<String, Object> uMap = new HashMap<>();
                uMap.put("id", p.getUser().getId().toString());
                uMap.put("fullName", p.getUser().getFullName());
                uMap.put("email", p.getUser().getEmail());
                pMap.put("user", uMap);
            } else {
                pMap.put("user", null);
            }
            txList.add(pMap);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("manualTotal", manualTotal);
        data.put("manualCount", manualCount);
        data.put("razorpayTotal", razorpayTotal);
        data.put("razorpayCount", razorpayCount);
        data.put("transactions", txList);
        data.put("page", paymentPage.getNumber());
        data.put("size", paymentPage.getSize());
        data.put("totalPages", paymentPage.getTotalPages());
        data.put("totalElements", paymentPage.getTotalElements());

        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @PostMapping("/box-office/buy")
    public ResponseEntity<ApiResponse<Map<String, Object>>> boxOfficeBuy(
            @Valid @RequestBody BoxOfficeRequest request) {

        User adminUser = securityContextService.getCurrentUser();
        staffPermissionService.checkPermission(adminUser, "BOX_OFFICE");

        List<BoxOfficeRequest.Item> items = request.getItems();
        if (items == null || items.isEmpty()) {
            if (request.getTierId() == null || request.getQuantity() == null) {
                throw new IllegalArgumentException("Either items list or tier_id & quantity must be provided");
            }
            BoxOfficeRequest.Item singleItem = new BoxOfficeRequest.Item();
            singleItem.setTierId(request.getTierId());
            singleItem.setQuantity(request.getQuantity());
            items = List.of(singleItem);
        }

        List<SpectatorTicket> tickets = checkoutService.boxOfficeCheckout(
                request.getEmail(),
                request.getFullName(),
                request.getPhone(),
                items
        );

        List<Map<String, Object>> ticketList = new ArrayList<>();
        for (SpectatorTicket t : tickets) {
            Map<String, Object> tMap = new HashMap<>();
            tMap.put("code", t.getTicketCode());
            tMap.put("tierName", t.getTier() != null ? t.getTier().getName() : "General Admission");
            tMap.put("status", t.getStatus().name());
            ticketList.add(tMap);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("message", "Tickets successfully issued via Box Office!");
        data.put("tickets", ticketList);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(data));
    }

    @GetMapping("/tickets/export-csv")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public void exportTicketsCsv(
            @RequestParam(value = "startDate", required = false) String startDateStr,
            @RequestParam(value = "endDate", required = false) String endDateStr,
            jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {

        User adminUser = securityContextService.getCurrentUser();
        staffPermissionService.checkPermission(adminUser, "TREASURY");

        OffsetDateTime start = parseDateTime(startDateStr, false);
        OffsetDateTime end = parseDateTime(endDateStr, true);

        String filename = "ticket_sales";
        if (startDateStr != null && !startDateStr.trim().isEmpty()) {
            filename += "_" + startDateStr.trim();
        }
        if (endDateStr != null && !endDateStr.trim().isEmpty()) {
            filename += "_to_" + endDateStr.trim();
        }
        filename += ".csv";

        response.setContentType("text/csv");
        response.setCharacterEncoding("UTF-8");
        response.setHeader(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");

        try (java.io.PrintWriter writer = response.getWriter();
             java.util.stream.Stream<SpectatorTicket> ticketStream = ticketRepository.streamByBookedAtBetween(start, end)) {
            
            writer.write("Ticket Code,Tier Name,Price,Status,Buyer Name,Buyer Email,WhatsApp,Payment Provider,Payment Status,Purchase Date\n");
            
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter
                    .ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(java.time.ZoneId.of("Asia/Kolkata"));

            ticketStream.forEach(t -> {
                String code = t.getTicketCode();
                String tierName = t.getTier() != null ? t.getTier().getName() : "General Admission";
                String price = t.getTier() != null ? t.getTier().getPrice().toString() : "0.00";
                String status = t.getStatus().name();
                
                String buyerName = t.getUser() != null ? t.getUser().getFullName() : "";
                String buyerEmail = t.getUser() != null ? t.getUser().getEmail() : "";
                String buyerPhone = t.getUser() != null ? t.getUser().getWhatsapp() : "";
                
                String provider = t.getPayment() != null ? t.getPayment().getProvider().name() : "";
                String payStatus = t.getPayment() != null ? t.getPayment().getStatus().name() : "";
                
                String purchaseDate = t.getBookedAt() != null ? t.getBookedAt().format(formatter) : "";

                StringBuilder line = new StringBuilder();
                line.append(escapeCsvField(code)).append(",")
                    .append(escapeCsvField(tierName)).append(",")
                    .append(escapeCsvField(price)).append(",")
                    .append(escapeCsvField(status)).append(",")
                    .append(escapeCsvField(buyerName)).append(",")
                    .append(escapeCsvField(buyerEmail)).append(",")
                    .append(escapeCsvField(buyerPhone)).append(",")
                    .append(escapeCsvField(provider)).append(",")
                    .append(escapeCsvField(payStatus)).append(",")
                    .append(escapeCsvField(purchaseDate)).append("\n");
                writer.write(line.toString());
            });
            writer.flush();
        }
    }

    private OffsetDateTime parseDateTime(String dateStr, boolean isEnd) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return isEnd ? OffsetDateTime.now() : OffsetDateTime.of(1970, 1, 1, 0, 0, 0, 0, java.time.ZoneOffset.UTC);
        }
        try {
            // Check if it's just a date (yyyy-MM-dd)
            if (dateStr.matches("^\\d{4}-\\d{2}-\\d{2}$")) {
                dateStr += isEnd ? "T23:59:59+05:30" : "T00:00:00+05:30";
            }
            return OffsetDateTime.parse(dateStr);
        } catch (Exception e) {
            try {
                return OffsetDateTime.parse(dateStr + "T00:00:00Z");
            } catch (Exception ex) {
                return isEnd ? OffsetDateTime.now() : OffsetDateTime.of(1970, 1, 1, 0, 0, 0, 0, java.time.ZoneOffset.UTC);
            }
        }
    }

    private String escapeCsvField(String field) {
        if (field == null) {
            return "";
        }
        String value = field.replace("\"", "\"\"");
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value + "\"";
        }
        return value;
    }

    @GetMapping("/checkins/recent")
    public ResponseEntity<ApiResponse<List<CheckinFeedEventDto>>> getRecentCheckins() {
        User user = securityContextService.getCurrentUser();
        staffPermissionService.checkPermission(user, "LIVE_CHECKIN");
        return ResponseEntity.ok(ApiResponse.success(liveCheckinService.getRecentCheckins()));
    }

    @GetMapping(value = "/checkins/stream", produces = "text/event-stream")
    public SseEmitter getCheckinStream() {
        User user = securityContextService.getCurrentUser();
        staffPermissionService.checkPermission(user, "LIVE_CHECKIN");
        return liveCheckinService.subscribe();
    }

    @GetMapping("/permissions/staff")
    public ResponseEntity<ApiResponse<List<StaffPermission>>> getAllStaffPermissions() {
        User user = securityContextService.getCurrentUser();
        securityContextService.checkAdminOnly(user);
        return ResponseEntity.ok(ApiResponse.success(staffPermissionService.getAllPermissions()));
    }

    @PostMapping("/permissions/staff")
    public ResponseEntity<ApiResponse<StaffPermission>> grantStaffPermission(
            @Valid @RequestBody StaffPermissionRequest request) {
        User user = securityContextService.getCurrentUser();
        securityContextService.checkAdminOnly(user);
        StaffPermission perm = staffPermissionService.grantPermission(request.getEmail(), request.getFeature());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(perm));
    }

    @DeleteMapping("/permissions/staff")
    public ResponseEntity<ApiResponse<Map<String, Object>>> revokeStaffPermission(
            @RequestParam("email") String email,
            @RequestParam("feature") String feature) {
        User user = securityContextService.getCurrentUser();
        securityContextService.checkAdminOnly(user);
        staffPermissionService.revokePermission(email, feature);
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Permission successfully revoked!");
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/permissions/my")
    public ResponseEntity<ApiResponse<List<String>>> getMyPermissions() {
        User user = securityContextService.getCurrentUser();
        if (user.getRole() == UserRole.admin) {
            return ResponseEntity.ok(ApiResponse.success(List.of("TREASURY", "BOX_OFFICE", "DRIFTER_REVIEW", "INFLUENCER_MGMT", "LIVE_CHECKIN")));
        }
        if (user.getRole() != UserRole.staff) {
            throw new UnauthorizedException("Access denied. Admin or Staff privileges required.");
        }
        List<StaffPermission> permissions = staffPermissionService.getPermissionsByEmail(user.getEmail());
        List<String> features = permissions.stream().map(StaffPermission::getFeature).toList();
        return ResponseEntity.ok(ApiResponse.success(features));
    }

    @PostMapping("/tickets/{ticketCode}/send-email")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sendTicketEmail(
            @PathVariable("ticketCode") String ticketCode) {
        
        User adminUser = securityContextService.getCurrentUser();
        securityContextService.checkAdminOrStaff(adminUser);
        
        SpectatorTicket ticket = ticketRepository.findByTicketCodeWithDetails(ticketCode)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found with code: " + ticketCode));
        
        User ticketUser = ticket.getUser();
        if (ticketUser == null) {
            throw new BadValidationException("Ticket has no associated user.");
        }
        
        if (!ticketUser.isEmailVerified()) {
            throw new BadValidationException("User associated with this ticket is not verified with our backend.");
        }
        
        List<SpectatorTicket> ticketsToSend;
        if (ticket.getPayment() != null) {
            ticketsToSend = ticketRepository.findByPaymentId(ticket.getPayment().getId());
            for (SpectatorTicket t : ticketsToSend) {
                if (t.getUser() != null) t.getUser().getEmail();
                if (t.getEvent() != null) t.getEvent().getName();
                if (t.getTier() != null) t.getTier().getName();
                if (t.getPayment() != null) {
                    t.getPayment().getId();
                    if (t.getPayment().getUser() != null) {
                        t.getPayment().getUser().getEmail();
                    }
                }
            }
        } else {
            ticketsToSend = List.of(ticket);
        }
        
        emailNotificationService.sendTicketConfirmationEmail(ticketsToSend);
        
        Map<String, Object> data = new HashMap<>();
        data.put("message", "Ticket confirmation email successfully sent to " + ticketUser.getEmail());
        data.put("ticketCode", ticketCode);
        data.put("recipient", ticketUser.getEmail());
        
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @PostMapping("/tickets/send-email-by-user")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sendTicketsEmailByUser(
            @RequestParam("email") String email) {
        
        User adminUser = securityContextService.getCurrentUser();
        securityContextService.checkAdminOrStaff(adminUser);
        
        User ticketUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
        
        if (!ticketUser.isEmailVerified()) {
            throw new BadValidationException("User associated with this email is not verified with our backend.");
        }
        
        List<SpectatorTicket> allTickets = ticketRepository.findByUserId(ticketUser.getId());
        List<SpectatorTicket> ticketsToSend = allTickets.stream()
                .filter(t -> t.getEvent() != null && t.getEvent().getEventDate() != null && t.getEvent().getEventDate().isAfter(OffsetDateTime.now()))
                .toList();
        if (ticketsToSend.isEmpty()) {
            throw new BadValidationException("No upcoming tickets found for user: " + email);
        }
        
        // Eager load details for each ticket
        for (SpectatorTicket t : ticketsToSend) {
            if (t.getUser() != null) t.getUser().getEmail();
            if (t.getEvent() != null) t.getEvent().getName();
            if (t.getTier() != null) t.getTier().getName();
            if (t.getPayment() != null) {
                t.getPayment().getId();
                if (t.getPayment().getUser() != null) {
                    t.getPayment().getUser().getEmail();
                }
            }
        }
        
        emailNotificationService.sendTicketConfirmationEmail(ticketsToSend);
        
        Map<String, Object> data = new HashMap<>();
        data.put("message", "Ticket confirmation email successfully sent to " + ticketUser.getEmail());
        data.put("recipient", ticketUser.getEmail());
        data.put("ticketCount", ticketsToSend.size());
        
        return ResponseEntity.ok(ApiResponse.success(data));
    }
}

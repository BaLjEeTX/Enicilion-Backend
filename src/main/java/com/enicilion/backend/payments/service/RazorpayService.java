package com.enicilion.backend.payments.service;

import com.enicilion.backend.common.exception.BadValidationException;
import com.enicilion.backend.common.exception.ResourceNotFoundException;
import com.enicilion.backend.common.service.SecureTokenService;
import com.enicilion.backend.payments.entity.Payment;
import com.enicilion.backend.payments.entity.PaymentStatus;
import com.enicilion.backend.payments.repository.PaymentRepository;
import com.enicilion.backend.tickets.entity.SpectatorTicket;
import com.enicilion.backend.tickets.entity.TicketStatus;
import com.enicilion.backend.tickets.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.*;
import com.enicilion.backend.coupons.entity.Coupon;
import com.enicilion.backend.coupons.entity.CouponUsage;
import com.enicilion.backend.coupons.repository.CouponRepository;
import com.enicilion.backend.coupons.repository.CouponUsageRepository;
import com.enicilion.backend.influencer.repository.InfluencerEarningsLedgerRepository;
import com.enicilion.backend.notification.service.NotificationService;
import com.enicilion.backend.notification.dto.TicketNotificationRequest;
import com.enicilion.backend.tickets.service.RedisInventoryService;

@Service
@RequiredArgsConstructor
@Slf4j
public class RazorpayService {

    @Value("${app.razorpay.key-id}")
    private String keyId;

    @Value("${app.razorpay.key-secret}")
    private String keySecret;

    @Value("${app.razorpay.sandbox-bypass-allowed:false}")
    private boolean sandboxBypassAllowed;

    private final PaymentRepository paymentRepository;
    private final TicketRepository ticketRepository;
    private final CouponRepository couponRepository;
    private final CouponUsageRepository couponUsageRepository;
    private final InfluencerEarningsLedgerRepository influencerEarningsLedgerRepository;
    private final NotificationService notificationService;
    private final RedisInventoryService redisInventoryService;
    private final SecureTokenService secureTokenService;

    public boolean verifySignature(String orderId, String razorpayPaymentId, String signature) {
        log.info("verifySignature called with orderId={}, razorpayPaymentId={}, signature={}", orderId, razorpayPaymentId, signature);
        log.info("verifySignature keySecret (length={}): {}", (keySecret != null ? keySecret.length() : 0), 
                 (keySecret != null && keySecret.length() > 4 ? keySecret.substring(0, 4) + "..." : "null"));
        if (sandboxBypassAllowed && "calculated_signature_matches_automatically_in_sandbox".equals(signature)) {
            log.info("Sandbox bypass allowed and matched dummy signature.");
            return true;
        }
        try {
            String payload = orderId + "|" + razorpayPaymentId;
            Mac sha256Hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(keySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256Hmac.init(secretKey);
            
            byte[] rawHmac = sha256Hmac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : rawHmac) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            String computed = hexString.toString();
            boolean matches = computed.equalsIgnoreCase(signature);
            log.info("Computed signature: {}, matches: {}", computed, matches);
            return matches;
        } catch (Exception e) {
            log.error("Error verifying Razorpay signature", e);
            return false;
        }
    }

    @Transactional
    public Map<String, Object> verifyAndProcessPayment(UUID paymentId, String orderId, String razorpayPaymentId, String signature) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        if (payment.getStatus() == PaymentStatus.paid) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "paid");
            response.put("paymentId", payment.getId().toString());
            response.put("secureToken", secureTokenService.generatePaymentToken(paymentId));
            
            List<SpectatorTicket> tickets = ticketRepository.findByPaymentId(paymentId);
            List<Map<String, Object>> ticketsList = new java.util.ArrayList<>();
            for (SpectatorTicket ticket : tickets) {
                Map<String, Object> tMap = new HashMap<>();
                tMap.put("code", ticket.getTicketCode());
                tMap.put("tierName", ticket.getTier() != null ? ticket.getTier().getName() : "General Admission");
                tMap.put("status", ticket.getStatus().name());
                tMap.put("secureToken", secureTokenService.generateTicketToken(ticket.getTicketCode(), ticket.getUser() != null ? ticket.getUser().getEmail() : ""));
                ticketsList.add(tMap);
            }
            response.put("tickets", ticketsList);
            return response;
        }

        boolean verified = verifySignature(orderId, razorpayPaymentId, signature);
        if (!verified) {
            payment.setStatus(PaymentStatus.failed);
            paymentRepository.save(payment);
            
            // Cancel associated tickets and release their inventory in Redis
            List<SpectatorTicket> tickets = ticketRepository.findByPaymentId(paymentId);
            OffsetDateTime now = OffsetDateTime.now();
            for (SpectatorTicket ticket : tickets) {
                ticket.setStatus(TicketStatus.cancelled);
                ticket.setUpdatedAt(now);
                if (ticket.getTier() != null) {
                    redisInventoryService.releaseInventory(ticket.getTier().getId(), 1);
                }
            }
            ticketRepository.saveAll(tickets);
            Map<String, Object> failureResult = new HashMap<>();
            failureResult.put("status", "failed");
            failureResult.put("paymentId", payment.getId().toString());
            return failureResult;
        }

        List<SpectatorTicket> tickets = ticketRepository.findByPaymentId(paymentId);
        boolean hasExpiredTickets = tickets.stream().anyMatch(t -> t.getStatus() == TicketStatus.cancelled);
        if (hasExpiredTickets) {
            log.warn("Payment verification conflict for payment {}: booking has expired and tickets are cancelled.", paymentId);
            payment.setStatus(PaymentStatus.failed);
            payment.setProviderTxId(razorpayPaymentId);
            paymentRepository.save(payment);

            // Ensure all tickets associated remain cancelled
            OffsetDateTime nowExpired = OffsetDateTime.now();
            for (SpectatorTicket ticket : tickets) {
                if (ticket.getStatus() != TicketStatus.cancelled) {
                    ticket.setStatus(TicketStatus.cancelled);
                    ticket.setUpdatedAt(nowExpired);
                }
            }
            ticketRepository.saveAll(tickets);
            Map<String, Object> failureResult = new HashMap<>();
            failureResult.put("status", "failed");
            failureResult.put("paymentId", payment.getId().toString());
            failureResult.put("message", "Booking expired and tickets were cancelled.");
            return failureResult;
        }

        payment.setStatus(PaymentStatus.paid);
        payment.setProviderTxId(razorpayPaymentId);
        payment.setPaidAt(OffsetDateTime.now());
        paymentRepository.save(payment);

        // Update status of all associated tickets to paid
        OffsetDateTime nowPaid = OffsetDateTime.now();
        for (SpectatorTicket ticket : tickets) {
            ticket.setStatus(TicketStatus.paid);
            ticket.setUpdatedAt(nowPaid);
        }
        ticketRepository.saveAll(tickets);

        // Confirm and finalize coupon usage if any
        Optional<CouponUsage> optUsage = couponUsageRepository.findByPaymentId(paymentId);
        if (optUsage.isPresent()) {
            CouponUsage usage = optUsage.get();
            usage.setUsedAt(OffsetDateTime.now());
            couponUsageRepository.save(usage);

            Coupon coupon = usage.getCoupon();
            coupon.setUsedCount(coupon.getUsedCount() + 1);
            couponRepository.save(coupon);

            // Approve influencer commissions if this is an influencer coupon
            if (coupon.isInfluencerCoupon()) {
                List<com.enicilion.backend.influencer.entity.InfluencerEarningsLedger> ledgerEntries = 
                        influencerEarningsLedgerRepository.findByPaymentId(paymentId);
                for (com.enicilion.backend.influencer.entity.InfluencerEarningsLedger entry : ledgerEntries) {
                    entry.setStatus(com.enicilion.backend.influencer.entity.LedgerStatus.approved);
                    entry.setUpdatedAt(OffsetDateTime.now());
                    influencerEarningsLedgerRepository.save(entry);
                }
            }
        }

        // Eagerly initialize lazy proxies (User, Tier, Event, Payment) within the active transaction
        // to prevent lazy-load failures on detached proxies in the async threads.
        for (SpectatorTicket ticket : tickets) {
            if (ticket.getUser() != null) {
                ticket.getUser().getEmail();
                ticket.getUser().getFullName();
                ticket.getUser().getWhatsapp();
            }
            if (ticket.getTier() != null) {
                ticket.getTier().getName();
                ticket.getTier().getPrice();
            }
            if (ticket.getEvent() != null) {
                ticket.getEvent().getName();
                ticket.getEvent().getEventDate();
                ticket.getEvent().getLocation();
            }
            if (ticket.getPayment() != null) {
                ticket.getPayment().getId();
                ticket.getPayment().getAmount();
                ticket.getPayment().getCurrency();
                ticket.getPayment().getPaidAt();
                ticket.getPayment().getProvider();
                if (ticket.getPayment().getUser() != null) {
                    ticket.getPayment().getUser().getEmail();
                    ticket.getPayment().getUser().getFullName();
                    ticket.getPayment().getUser().getWhatsapp();
                }
            }
        }

        // Queue ticket confirmation task in outbox for asynchronous processing
        if (!tickets.isEmpty()) {
            SpectatorTicket first = tickets.get(0);
            notificationService.queueTicketConfirmation(first.getTicketCode(), null, null);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("status", "paid");
        response.put("paymentId", payment.getId().toString());
        response.put("secureToken", secureTokenService.generatePaymentToken(paymentId));

        List<Map<String, Object>> ticketsList = new java.util.ArrayList<>();
        for (SpectatorTicket ticket : tickets) {
            Map<String, Object> tMap = new HashMap<>();
            tMap.put("code", ticket.getTicketCode());
            tMap.put("tierName", ticket.getTier() != null ? ticket.getTier().getName() : "General Admission");
            tMap.put("status", ticket.getStatus().name());
            tMap.put("secureToken", secureTokenService.generateTicketToken(ticket.getTicketCode(), ticket.getUser() != null ? ticket.getUser().getEmail() : ""));
            ticketsList.add(tMap);
        }
        response.put("tickets", ticketsList);

        return response;
    }
}

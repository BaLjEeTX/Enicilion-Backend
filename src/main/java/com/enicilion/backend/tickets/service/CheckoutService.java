package com.enicilion.backend.tickets.service;

import com.enicilion.backend.auth.entity.User;
import com.enicilion.backend.common.exception.*;
import com.enicilion.backend.coupons.entity.Coupon;
import com.enicilion.backend.coupons.entity.CouponUsage;
import com.enicilion.backend.coupons.repository.CouponRepository;
import com.enicilion.backend.coupons.repository.CouponUsageRepository;
import com.enicilion.backend.coupons.service.CouponService;
import com.enicilion.backend.payments.entity.Payment;
import com.enicilion.backend.payments.entity.PaymentProvider;
import com.enicilion.backend.payments.entity.PaymentStatus;
import com.enicilion.backend.payments.entity.ReferenceType;
import com.enicilion.backend.payments.repository.PaymentRepository;
import com.enicilion.backend.tickets.dto.CartItem;
import com.enicilion.backend.tickets.dto.CheckoutRequest;
import com.enicilion.backend.tickets.dto.PaymentResponse;
import com.enicilion.backend.tickets.dto.BoxOfficeRequest;
import com.enicilion.backend.tickets.entity.*;
import com.enicilion.backend.tickets.repository.TicketRepository;
import com.enicilion.backend.tickets.repository.TicketTierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.enicilion.backend.auth.repository.UserRepository;
import com.enicilion.backend.influencer.repository.InfluencerEarningsLedgerRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import java.nio.charset.StandardCharsets;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class CheckoutService {

    private final TicketTierRepository tierRepository;
    private final TicketRepository ticketRepository;
    private final CouponRepository couponRepository;
    private final CouponService couponService;
    private final CouponUsageRepository couponUsageRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final InfluencerEarningsLedgerRepository influencerEarningsLedgerRepository;
    private final RedisInventoryService redisInventoryService;
    private final com.enicilion.backend.auth.service.ReferralCodeService referralCodeService;
    private final RestTemplate restTemplate;

    @Value("${app.razorpay.key-id}")
    private String keyId;

    @Value("${app.razorpay.key-secret}")
    private String keySecret;


    @Transactional
    public PaymentResponse checkout(CheckoutRequest request, User user, String idempotencyKeyHeader) {
        // 1. Resolve guest user if authenticated user is absent
        if (user == null) {
            String guestEmail = request.getGuestEmail();
            if (guestEmail == null || guestEmail.trim().isEmpty()) {
                throw new BadValidationException("Email is required for checkout.");
            }
            guestEmail = guestEmail.trim().toLowerCase();

            Optional<User> existingUser = userRepository.findByEmail(guestEmail);
            if (existingUser.isPresent()) {
                user = existingUser.get();
            } else {
                String guestName = request.getGuestName();
                if (guestName == null || guestName.trim().isEmpty()) {
                    guestName = "Guest";
                }

                String referralCode = referralCodeService.generateUniqueReferralCode(guestName);

                user = User.builder()
                        .fullName(guestName)
                        .email(guestEmail)
                        .whatsapp(request.getGuestPhone())
                        .passwordHash("GUEST_" + UUID.randomUUID().toString())
                        .role(com.enicilion.backend.auth.entity.UserRole.user)
                        .referralCode(referralCode)
                        .referredBy(null)
                        .build();
                userRepository.save(user);
            }
        }

        // 2. Resolve Idempotency Key
        String idempotencyKey = request.getIdempotencyKey() != null 
                ? request.getIdempotencyKey() 
                : idempotencyKeyHeader;

        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            throw new BadValidationException("Idempotency key is required for checkout.");
        }

        // Check if payment already exists for this idempotency key
        Optional<Payment> existingPayment = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if (existingPayment.isPresent()) {
            Payment payment = existingPayment.get();
            log.info("Duplicate checkout request detected. Returning existing payment: {}", payment.getId());
            return new PaymentResponse(payment.getId().toString(), payment.getAmount(), payment.getCurrency(), payment.getProviderSession(), keyId);
        }

        // Resolve coupon if provided
        String couponCode = normalizeCouponCode(request.getCouponCode());
        final Coupon coupon;
        if (couponCode != null) {
            coupon = couponRepository.findByCodeForUpdate(couponCode)
                    .orElseThrow(() -> new ResourceNotFoundException("Invalid or inactive coupon"));

            try {
                couponService.validateCouponState(coupon);
            } catch (IllegalArgumentException e) {
                if (e.getMessage().contains("reserved")) {
                    throw new CouponReservationException("Coupon is currently reserved by another pending transaction");
                } else if (e.getMessage().contains("used")) {
                    throw new CouponReservationException("Coupon already used");
                } else {
                    throw new BadValidationException(e.getMessage());
                }
            }
        } else {
            coupon = null;
        }

        BigDecimal totalBaseAmount = BigDecimal.ZERO;
        int totalAccessTickets = 0;
        int eligibleCouponTickets = 0;
        BigDecimal eligibleCouponSubtotal = BigDecimal.ZERO;

        // 2. Sort items by Tier ID to prevent database deadlock locks
        List<CartItem> items = new ArrayList<>(request.getItems());
        items.sort(Comparator.comparing(CartItem::getTierId));

        // Try reserving all items in Redis first
        List<CartItem> reservedItems = new ArrayList<>();
        try {
            for (CartItem item : items) {
                boolean reserved = redisInventoryService.tryReserveInventory(item.getTierId(), item.getQuantity());
                if (!reserved) {
                    throw new InventoryExhaustedException("Tickets sold out or unavailable for tier ID: " + item.getTierId());
                }
                reservedItems.add(item);
            }
        } catch (Exception e) {
            // Rollback any successful reservations in this batch
            for (CartItem item : reservedItems) {
                redisInventoryService.releaseInventory(item.getTierId(), item.getQuantity());
            }
            throw e;
        }

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                        log.info("Database transaction rolled back. Releasing reserved Redis inventory.");
                        for (CartItem item : reservedItems) {
                            try {
                                redisInventoryService.releaseInventory(item.getTierId(), item.getQuantity());
                            } catch (Exception ex) {
                                log.error("Failed to release Redis inventory on transaction rollback for tier {}", item.getTierId(), ex);
                            }
                        }
                    }
                }
            });
        }

        // Create a list to cache loaded Tiers and matching quantities
        List<LockedTierItem> lockedTiers = new ArrayList<>();

        for (CartItem item : items) {
            // Lock Row using PESSIMISTIC_WRITE (generates SELECT FOR UPDATE)
            TicketTier tier = tierRepository.findByIdForUpdate(item.getTierId())
                    .orElseThrow(() -> new ResourceNotFoundException("Ticket tier not found: " + item.getTierId()));

            BigDecimal overridePrice = getOverridePrice(tier.getName(), tier.getPrice());
            BigDecimal itemSubtotal = overridePrice.multiply(BigDecimal.valueOf(item.getQuantity()));

            // Group discount: If purchasing 5+ tickets of the same type, apply 10% discount
            BigDecimal bulkDiscount = BigDecimal.ZERO;
            if (item.getQuantity() >= 5) {
                bulkDiscount = itemSubtotal.multiply(BigDecimal.valueOf(0.10)).setScale(2, RoundingMode.HALF_UP);
                itemSubtotal = itemSubtotal.subtract(bulkDiscount);
            }

            totalBaseAmount = totalBaseAmount.add(itemSubtotal);

            if (!isFoodAndBeverage(tier.getName())) {
                totalAccessTickets += item.getQuantity();
            }

            // Exclude already discounted lines from coupon calculations
            boolean isCouponEligible = isEligibleForCoupon(tier.getName()) && (item.getQuantity() < 5);

            // Check event restriction if coupon is set
            if (isCouponEligible && coupon != null && coupon.getApplicableEvent() != null) {
                if (!coupon.getApplicableEvent().getId().equals(tier.getEvent().getId())) {
                    isCouponEligible = false;
                }
            }

            if (isCouponEligible) {
                eligibleCouponTickets += item.getQuantity();
                eligibleCouponSubtotal = eligibleCouponSubtotal.add(itemSubtotal);
            }

            lockedTiers.add(new LockedTierItem(tier, item.getQuantity()));
        }

        // Apply coupon code discount if applicable
        BigDecimal discount = BigDecimal.ZERO;
        if (coupon != null) {
            BigDecimal discountRate = BigDecimal.valueOf(coupon.getDiscountPercentage()).divide(BigDecimal.valueOf(100.0));
            discount = eligibleCouponSubtotal.multiply(discountRate).setScale(2, RoundingMode.HALF_UP);
        }

        // Service Fee calculation (49 INR per ticket access fee)
        BigDecimal serviceFee = BigDecimal.valueOf(49.00).multiply(BigDecimal.valueOf(totalAccessTickets));
        BigDecimal finalAmount = totalBaseAmount.add(serviceFee).subtract(discount).setScale(0, RoundingMode.HALF_UP);

        // Verify client calculation
        if (request.getClientTotal() != null && request.getClientTotal().compareTo(finalAmount) != 0) {
            throw new BadValidationException("Checkout total mismatch. Calculated: " + finalAmount + ", client sent: " + request.getClientTotal());
        }

        // 3. Create Payment Intent
        Payment payment = Payment.builder()
                .user(user)
                .amount(finalAmount)
                .provider(PaymentProvider.razorpay)
                .status(PaymentStatus.pending)
                .idempotencyKey(idempotencyKey)
                .referenceType(ReferenceType.payment_intent)
                .build();
        paymentRepository.save(payment);

        String providerOrderId = createRazorpayOrder(payment.getId(), finalAmount);
        payment.setProviderSession(providerOrderId);
        paymentRepository.save(payment);

        // 4. Reserve Coupon
        if (coupon != null) {
            CouponUsage usage = CouponUsage.builder()
                    .coupon(coupon)
                    .payment(payment)
                    .usedAt(OffsetDateTime.now())
                    .build();
            couponUsageRepository.save(usage);
        }

        // Calculate division parameters to allocate coupon discounts safely
        BigDecimal perTicketDiscount = BigDecimal.ZERO;
        if (discount.compareTo(BigDecimal.ZERO) > 0 && eligibleCouponTickets > 0) {
            perTicketDiscount = discount.divide(BigDecimal.valueOf(eligibleCouponTickets), 2, RoundingMode.HALF_UP);
        }

        // 5. Create Booked Tickets
        List<SpectatorTicket> ticketsToSave = new ArrayList<>();
        List<com.enicilion.backend.influencer.entity.InfluencerEarningsLedger> ledgerEntriesToSave = new ArrayList<>();

        for (LockedTierItem item : lockedTiers) {
            TicketTier tier = item.getTier();
            boolean isTierCouponEligible = isEligibleForCoupon(tier.getName());

            // Check event restriction
            if (isTierCouponEligible && coupon != null && coupon.getApplicableEvent() != null) {
                if (!coupon.getApplicableEvent().getId().equals(tier.getEvent().getId())) {
                    isTierCouponEligible = false;
                }
            }

            for (int i = 0; i < item.getQuantity(); i++) {
                int discountValue = isTierCouponEligible ? perTicketDiscount.intValue() : 0;
                SpectatorTicket ticket = SpectatorTicket.builder()
                        .event(tier.getEvent())
                        .user(user)
                        .tier(tier)
                        .payment(payment)
                        .ticketCode(TicketCodeGenerator.generateSecureCode(tier.getName()))
                        .status(TicketStatus.booked)
                        .discountApplied(discountValue)
                        .referralCodeUsed(user.getReferredBy())
                        .build();
                ticketsToSave.add(ticket);

                // Create pending influencer commission record if applicable
                if (coupon != null && coupon.isInfluencerCoupon() && coupon.getInfluencerProfile() != null && isTierCouponEligible) {
                    BigDecimal ticketPricePaid = tier.getPrice().subtract(BigDecimal.valueOf(discountValue));
                    BigDecimal commissionAmount = BigDecimal.ZERO;
                    var profile = coupon.getInfluencerProfile();
                    if (profile.getCommissionType() == com.enicilion.backend.influencer.entity.CommissionType.fixed) {
                        commissionAmount = profile.getCommissionValue();
                    } else if (profile.getCommissionType() == com.enicilion.backend.influencer.entity.CommissionType.percentage) {
                        commissionAmount = ticketPricePaid.multiply(profile.getCommissionValue().divide(BigDecimal.valueOf(100.0))).setScale(2, RoundingMode.HALF_UP);
                    }

                    com.enicilion.backend.influencer.entity.InfluencerEarningsLedger ledger = com.enicilion.backend.influencer.entity.InfluencerEarningsLedger.builder()
                            .influencerProfile(profile)
                            .payment(payment)
                            .ticket(ticket)
                            .amount(commissionAmount)
                            .status(com.enicilion.backend.influencer.entity.LedgerStatus.pending)
                            .build();
                    ledgerEntriesToSave.add(ledger);
                }
            }
        }

        ticketRepository.saveAll(ticketsToSave);
        if (!ledgerEntriesToSave.isEmpty()) {
            influencerEarningsLedgerRepository.saveAll(ledgerEntriesToSave);
        }

        return new PaymentResponse(payment.getId().toString(), finalAmount, payment.getCurrency(), payment.getProviderSession(), keyId);
    }

    @Transactional
    public List<SpectatorTicket> boxOfficeCheckout(String email, String fullName, String phone, UUID tierId, int quantity) {
        BoxOfficeRequest.Item item = new BoxOfficeRequest.Item();
        item.setTierId(tierId);
        item.setQuantity(quantity);
        return boxOfficeCheckout(email, fullName, phone, List.of(item));
    }

    @Transactional
    public List<SpectatorTicket> boxOfficeCheckout(String email, String fullName, String phone, List<BoxOfficeRequest.Item> items) {
        // 1. Resolve or Create User
        String cleanedEmail = email.trim().toLowerCase();
        User user = userRepository.findByEmail(cleanedEmail).orElseGet(() -> {
            String guestName = fullName == null || fullName.trim().isEmpty() ? "Guest" : fullName.trim();
            String referralCode = referralCodeService.generateUniqueReferralCode(guestName);
            User newUser = User.builder()
                    .fullName(guestName)
                    .email(cleanedEmail)
                    .whatsapp(phone)
                    .passwordHash("GUEST_" + UUID.randomUUID().toString())
                    .role(com.enicilion.backend.auth.entity.UserRole.user)
                    .referralCode(referralCode)
                    .build();
            return userRepository.save(newUser);
        });

        // 2. Sort items by Tier ID to prevent database deadlock locks
        List<BoxOfficeRequest.Item> sortedItems = new ArrayList<>(items);
        sortedItems.sort(Comparator.comparing(BoxOfficeRequest.Item::getTierId));

        // Try reserving all items in Redis first
        List<BoxOfficeRequest.Item> reservedItems = new ArrayList<>();
        try {
            for (BoxOfficeRequest.Item item : sortedItems) {
                boolean reserved = redisInventoryService.tryReserveInventory(item.getTierId(), item.getQuantity());
                if (!reserved) {
                    throw new InventoryExhaustedException("Tickets sold out or unavailable for tier ID: " + item.getTierId());
                }
                reservedItems.add(item);
            }
        } catch (Exception e) {
            // Rollback any successful reservations in this batch
            for (BoxOfficeRequest.Item item : reservedItems) {
                redisInventoryService.releaseInventory(item.getTierId(), item.getQuantity());
            }
            throw e;
        }

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                        log.info("Box office transaction rolled back. Releasing reserved Redis inventory.");
                        for (BoxOfficeRequest.Item item : reservedItems) {
                            try {
                                redisInventoryService.releaseInventory(item.getTierId(), item.getQuantity());
                            } catch (Exception ex) {
                                log.error("Failed to release Redis inventory on box office rollback for tier {}", item.getTierId(), ex);
                            }
                        }
                    }
                }
            });
        }

        BigDecimal totalBaseAmount = BigDecimal.ZERO;
        BigDecimal totalServiceFee = BigDecimal.ZERO;
        List<LockedTierItem> lockedTiers = new ArrayList<>();

        for (BoxOfficeRequest.Item item : sortedItems) {
            // Lock Row using PESSIMISTIC_WRITE
            TicketTier tier = tierRepository.findByIdForUpdate(item.getTierId())
                    .orElseThrow(() -> new ResourceNotFoundException("Ticket tier not found: " + item.getTierId()));

            BigDecimal itemBaseAmount = tier.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            totalBaseAmount = totalBaseAmount.add(itemBaseAmount);

            if (!isFoodAndBeverage(tier.getName())) {
                BigDecimal itemServiceFee = BigDecimal.valueOf(49.00).multiply(BigDecimal.valueOf(item.getQuantity()));
                totalServiceFee = totalServiceFee.add(itemServiceFee);
            }

            lockedTiers.add(new LockedTierItem(tier, item.getQuantity()));
        }

        BigDecimal finalAmount = totalBaseAmount.add(totalServiceFee).setScale(0, RoundingMode.HALF_UP);

        // 3. Create paid Payment
        Payment payment = Payment.builder()
                .user(user)
                .amount(finalAmount)
                .provider(PaymentProvider.manual)
                .status(PaymentStatus.paid)
                .idempotencyKey("manual_" + UUID.randomUUID().toString())
                .referenceType(ReferenceType.spectator_ticket)
                .paidAt(OffsetDateTime.now())
                .build();
        paymentRepository.save(payment);

        // 4. Create Paid Tickets
        List<SpectatorTicket> ticketsCreated = new ArrayList<>();
        for (LockedTierItem lockedItem : lockedTiers) {
            TicketTier tier = lockedItem.getTier();
            int qty = lockedItem.getQuantity();
            for (int i = 0; i < qty; i++) {
                SpectatorTicket ticket = SpectatorTicket.builder()
                        .event(tier.getEvent())
                        .user(user)
                        .tier(tier)
                        .payment(payment)
                        .ticketCode(TicketCodeGenerator.generateSecureCode(tier.getName()))
                        .status(TicketStatus.paid)
                        .discountApplied(0)
                        .referralCodeUsed(user.getReferredBy())
                        .build();
                ticketsCreated.add(ticket);
            }
        }
        ticketRepository.saveAll(ticketsCreated);

        return ticketsCreated;
    }

    private String createRazorpayOrder(UUID paymentId, BigDecimal amount) {
        if (keyId == null || keyId.trim().isEmpty() || keyId.startsWith("rzp_test_dummy") ||
            keySecret == null || keySecret.trim().isEmpty() || keySecret.startsWith("dummy_secret")) {
            log.info("Using dummy Razorpay order ID (sandbox/bypass or dummy keys detected)");
            return "order_dummy_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        }

        try {
            // Amount in paise (1 INR = 100 paise)
            long amountInPaise = amount.multiply(BigDecimal.valueOf(100)).longValue();
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("amount", amountInPaise);
            requestBody.put("currency", "INR");
            requestBody.put("receipt", paymentId.toString());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            String auth = keyId + ":" + keySecret;
            byte[] encodedAuth = Base64.getEncoder().encode(auth.getBytes(StandardCharsets.UTF_8));
            headers.set("Authorization", "Basic " + new String(encodedAuth));

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            String url = "https://api.razorpay.com/v1/orders";
            log.info("Sending request to Razorpay to create order for paymentId: {}, amount: {} paise", paymentId, amountInPaise);
            
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                String razorpayOrderId = (String) body.get("id");
                log.info("Razorpay order created successfully. Order ID: {}", razorpayOrderId);
                return razorpayOrderId;
            } else {
                throw new BadValidationException("Failed to create order on Razorpay: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Exception occurred while creating Razorpay order", e);
            throw new BadValidationException("Failed to initiate payment gateway: " + e.getMessage());
        }
    }

    private BigDecimal getOverridePrice(String name, BigDecimal price) {
        return price; // Place holder for custom pricing overrides
    }

    private boolean isFoodAndBeverage(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase();
        return lower.contains("food") || lower.contains("beverage") || lower.contains("drink") || lower.contains("spirit") || lower.contains("alcohol");
    }

    private boolean isEligibleForCoupon(String name) {
        return !isFoodAndBeverage(name);
    }

    private String normalizeCouponCode(String code) {
        if (code == null || code.trim().isEmpty()) return null;
        return code.trim().toUpperCase();
    }



    @lombok.Value
    private static class LockedTierItem {
        TicketTier tier;
        int quantity;
    }
}

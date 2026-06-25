package com.enicilion.backend.common.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

/**
 * Centralized service for generating secure tokens used to gate access to
 * ticket PDFs, wallet passes, and payment-scoped resources.
 * <p>
 * The salt is externalized to {@code app.security.token-salt} so it can be
 * rotated without code changes.
 */
@Service
public class SecureTokenService {

    @Value("${app.security.token-salt}")
    private String tokenSalt;

    /**
     * Generates a SHA-256 hex token for a single ticket.
     *
     * @param ticketCode the unique ticket code
     * @param email      the ticket holder's email (or empty string if guest)
     * @return lowercase hex SHA-256 hash
     */
    public String generateTicketToken(String ticketCode, String email) {
        String data = ticketCode + ":" + (email != null ? email : "") + ":" + tokenSalt;
        return sha256Hex(data);
    }

    /**
     * Generates a SHA-256 hex token for a payment (covers all tickets in an order).
     *
     * @param paymentId the payment UUID
     * @return lowercase hex SHA-256 hash
     */
    public String generatePaymentToken(UUID paymentId) {
        String data = paymentId.toString() + ":" + tokenSalt;
        return sha256Hex(data);
    }

    private String sha256Hex(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(64);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Error generating secure token", e);
        }
    }
}

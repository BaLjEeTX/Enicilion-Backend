package com.enicilion.backend.auth.service;

import com.enicilion.backend.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Generates unique referral codes for users.
 * Extracted from AuthService and CheckoutService to eliminate duplication.
 */
@Service
@RequiredArgsConstructor
public class ReferralCodeService {

    private final UserRepository userRepository;

    /**
     * Generates a unique referral code based on the user's name.
     * Takes the first 6 uppercase alpha characters of the name and appends
     * a random 4-character suffix, retrying until unique.
     *
     * @param name the user's full name
     * @return a unique referral code (e.g., "BALJEETA3F2")
     */
    public String generateUniqueReferralCode(String name) {
        String base = name.replaceAll("[^a-zA-Z]", "").toUpperCase();
        if (base.length() > 6) {
            base = base.substring(0, 6);
        } else if (base.isEmpty()) {
            base = "ENIC";
        }

        String code;
        do {
            String suffix = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
            code = base + suffix;
        } while (userRepository.findByReferralCode(code).isPresent());

        return code;
    }
}

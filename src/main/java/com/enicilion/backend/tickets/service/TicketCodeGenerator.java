package com.enicilion.backend.tickets.service;

import java.security.SecureRandom;

public class TicketCodeGenerator {
    private static final SecureRandom secureRandom = new SecureRandom();
    
    // High-readability alphabet excluding easily confused characters: 0, 1, I, O
    private static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    
    public static String generateSecureCode() {
        return generateSecureCode("GEN");
    }

    public static String generateSecureCode(String tierName) {
        String cleanTier = "GEN";
        if (tierName != null && !tierName.trim().isEmpty()) {
            cleanTier = tierName.replaceAll("[^a-zA-Z0-9]", "").toUpperCase();
            if (cleanTier.length() > 5) {
                cleanTier = cleanTier.substring(0, 5);
            }
        }

        StringBuilder randomPart = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            int index = secureRandom.nextInt(ALPHABET.length());
            randomPart.append(ALPHABET.charAt(index));
        }
        
        // System.currentTimeMillis in base-36 (length ~8). Take last 4 characters for suffix.
        String timeBase36 = Long.toString(System.currentTimeMillis(), 36).toUpperCase();
        String timePart = timeBase36.length() > 4 
                ? timeBase36.substring(timeBase36.length() - 4) 
                : timeBase36;
        
        // Generates safe, highly-readable, non-predictable codes with embedded tier name prefix
        return "TKT-" + cleanTier + "-" + randomPart.toString() + "-" + timePart;
    }
}

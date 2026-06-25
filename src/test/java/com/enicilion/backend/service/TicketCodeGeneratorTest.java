package com.enicilion.backend.service;

import com.enicilion.backend.tickets.service.TicketCodeGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TicketCodeGenerator.
 * Real format: TKT-{TIER}-{8 alphanum}-{4 base36 timestamp}
 */
class TicketCodeGeneratorTest {

    @Test
    @DisplayName("Generated code has correct prefix TKT-")
    void testGenerateSecureCodeHasPrefix() {
        String code = TicketCodeGenerator.generateSecureCode();
        assertNotNull(code);
        assertTrue(code.startsWith("TKT-"), "Expected 'TKT-' prefix, got: " + code);
    }

    @Test
    @DisplayName("Generated code has exactly 4 dash-separated parts")
    void testGenerateSecureCodeFormat() {
        String code = TicketCodeGenerator.generateSecureCode();
        String[] parts = code.split("-");
        assertEquals(4, parts.length, "Expected 4 parts in: " + code);
        assertEquals("TKT", parts[0]);
        assertEquals("GEN", parts[1]);   // Default tier
        assertEquals(8, parts[2].length(), "Random part must be 8 chars");
        assertEquals(4, parts[3].length(), "Timestamp suffix must be 4 chars");
    }

    @Test
    @DisplayName("Custom tier name is embedded in code")
    void testGenerateSecureCodeWithTierName() {
        String code = TicketCodeGenerator.generateSecureCode("VIP Premium");
        String[] parts = code.split("-");
        // VIP Premium → VIPPREMIUM → truncated to VIPPRE (first 5 if > 5 chars after strip)
        // "VIP Premium" → strip non-alphanum → "VIPPremium" → upper → "VIPPREMIUM" (10) → substr(0,5) = "VIPPR"
        assertEquals("VIPPR", parts[1], "Tier segment should be 5-char truncated: " + code);
    }

    @Test
    @DisplayName("Short tier names are kept in full")
    void testShortTierNameIsKeptFull() {
        String code = TicketCodeGenerator.generateSecureCode("GA");
        String[] parts = code.split("-");
        assertEquals("GA", parts[1]);
    }

    @Test
    @DisplayName("Null tier name defaults to GEN")
    void testNullTierDefaultsToGEN() {
        String code = TicketCodeGenerator.generateSecureCode(null);
        assertTrue(code.startsWith("TKT-GEN-"));
    }

    @Test
    @DisplayName("1000 codes generated must all be unique")
    void testUniqueness() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String code = TicketCodeGenerator.generateSecureCode();
            assertTrue(codes.add(code), "Duplicate code generated: " + code);
        }
    }

    @Test
    @DisplayName("Consecutive calls return different codes")
    void testConsecutiveCallsProduceDifferentCodes() {
        String code1 = TicketCodeGenerator.generateSecureCode();
        String code2 = TicketCodeGenerator.generateSecureCode();
        assertNotEquals(code1, code2);
    }

    @Test
    @DisplayName("Code only contains safe readable alphabet characters and hyphens")
    void testCodeCharacterSet() {
        // The generator uses alphabet "23456789ABCDEFGHJKLMNPQRSTUVWXYZ" + base-36 timestamp
        String code = TicketCodeGenerator.generateSecureCode();
        assertTrue(code.matches("TKT-[A-Z0-9]+-[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{8}-[A-Z0-9]{4}"),
                "Code does not match expected pattern: " + code);
    }
}

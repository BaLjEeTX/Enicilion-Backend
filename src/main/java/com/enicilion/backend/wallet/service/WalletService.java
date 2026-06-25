package com.enicilion.backend.wallet.service;

import com.enicilion.backend.tickets.entity.SpectatorTicket;
import com.enicilion.backend.wallet.config.WalletProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.brendamour.jpasskit.PKBarcode;
import de.brendamour.jpasskit.PKPass;
import de.brendamour.jpasskit.enums.PKBarcodeFormat;
import de.brendamour.jpasskit.passes.PKEventTicket;
import de.brendamour.jpasskit.PKField;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@Slf4j
@RequiredArgsConstructor
public class WalletService {

    private final WalletProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private PrivateKey dummyPrivateKey;
    
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("MMM dd, yyyy - hh:mm a")
            .withZone(ZoneId.of("Asia/Kolkata"));

    @PostConstruct
    public void init() throws Exception {
        if (properties.getGoogle().isMockMode()) {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            KeyPair kp = kpg.generateKeyPair();
            dummyPrivateKey = kp.getPrivate();
        }
    }

    public byte[] generateApplePass(SpectatorTicket ticket) {
        try {
            String eventName = ticket.getEvent().getName();
            String holderName = ticket.getUser().getFullName();
            String dateStr = TIME_FORMATTER.format(ticket.getEvent().getEventDate());
            String tierName = ticket.getTier() != null ? ticket.getTier().getName() : "General";

            // Build Pass structure
            PKPass pass = PKPass.builder()
                    .passTypeIdentifier(properties.getApple().getPassTypeIdentifier())
                    .teamIdentifier(properties.getApple().getTeamIdentifier())
                    .organizationName("MOTORSCAPE")
                    .description("Event Ticket for " + eventName)
                    .serialNumber(ticket.getTicketCode())
                    .formatVersion(1)
                    .logoText("MOTORSCAPE")
                    .foregroundColor("rgb(255, 255, 255)")
                    .backgroundColor("rgb(18, 18, 18)")
                    .labelColor("rgb(212, 175, 55)") // Gold
                    .barcodes(List.of(PKBarcode.builder()
                            .format(PKBarcodeFormat.PKBarcodeFormatQR)
                            .message(ticket.getTicketCode())
                            .messageEncoding(java.nio.charset.StandardCharsets.UTF_8)
                            .build()))
                    .pass(PKEventTicket.builder()
                            .primaryFields(List.of(
                                    PKField.builder().key("event").label("EVENT").value(eventName).build()
                            ))
                            .secondaryFields(List.of(
                                    PKField.builder().key("holder").label("TICKET HOLDER").value(holderName).build(),
                                    PKField.builder().key("date").label("DATE & TIME").value(dateStr).build()
                            ))
                            .auxiliaryFields(List.of(
                                    PKField.builder().key("tier").label("CATEGORY").value(tierName).build(),
                                    PKField.builder().key("venue").label("VENUE").value(ticket.getEvent().getLocation()).build()
                            ))
                            .backFields(List.of(
                                    PKField.builder().key("id").label("TICKET ID").value(ticket.getTicketCode()).build()
                            ))
                            .build())
                    .build();

            if (properties.getApple().isMockMode()) {
                log.info("Apple Wallet Mock Mode: Generating unsigned zip for ticket {}", ticket.getTicketCode());
                return generateMockUnsignedZip(pass);
            } else {
                // TODO: Implement actual signing using PKFileBasedSigningUtil if certificates are provided
                throw new UnsupportedOperationException("Production Apple Wallet signing not yet implemented locally.");
            }

        } catch (Exception e) {
            log.error("Failed to generate Apple Pass", e);
            throw new RuntimeException("Apple Pass Generation failed: " + e.getMessage());
        }
    }

    private byte[] generateMockUnsignedZip(PKPass pass) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // Write pass.json
            ZipEntry passJsonEntry = new ZipEntry("pass.json");
            zos.putNextEntry(passJsonEntry);
            byte[] passBytes = objectMapper.writeValueAsBytes(pass);
            zos.write(passBytes);
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    public String generateGoogleWalletJwt(SpectatorTicket ticket) {
        try {
            String issuerId = properties.getGoogle().getIssuerId();
            String classId = issuerId + "." + ticket.getEvent().getId().toString().replace("-", "");
            String objectId = issuerId + "." + ticket.getTicketCode();

            String eventName = ticket.getEvent().getName();
            String holderName = ticket.getUser().getFullName();
            String tierName = ticket.getTier() != null ? ticket.getTier().getName() : "General";

            // Build Google Wallet Object
            Map<String, Object> ticketObject = new HashMap<>();
            ticketObject.put("id", objectId);
            ticketObject.put("classId", classId);
            ticketObject.put("state", "ACTIVE");
            ticketObject.put("ticketHolderName", holderName);
            ticketObject.put("ticketType", Map.of("defaultValue", Map.of("language", "en-US", "value", tierName)));
            ticketObject.put("barcode", Map.of("type", "qrCode", "value", ticket.getTicketCode()));
            
            // Seat info or venue info can be placed here too

            // Build Google Wallet Class (Inline creation)
            Map<String, Object> ticketClass = new HashMap<>();
            ticketClass.put("id", classId);
            ticketClass.put("issuerName", "MOTORSCAPE");
            ticketClass.put("eventName", Map.of("defaultValue", Map.of("language", "en-US", "value", eventName)));

            Map<String, Object> payload = new HashMap<>();
            payload.put("eventTicketObjects", List.of(ticketObject));
            payload.put("eventTicketClasses", List.of(ticketClass));

            if (properties.getGoogle().isMockMode()) {
                log.info("Google Wallet Mock Mode: Generating mock JWT for ticket {}", ticket.getTicketCode());
                return Jwts.builder()
                        .claim("iss", "mock-service-account@mock.iam.gserviceaccount.com")
                        .claim("aud", "google")
                        .claim("typ", "savetowallet")
                        .claim("iat", System.currentTimeMillis() / 1000)
                        .claim("payload", payload)
                        .signWith(dummyPrivateKey)
                        .compact();
            } else {
                // TODO: Load actual service account credentials and sign
                throw new UnsupportedOperationException("Production Google Wallet signing not yet implemented locally.");
            }

        } catch (Exception e) {
            log.error("Failed to generate Google Wallet JWT", e);
            throw new RuntimeException("Google Wallet Generation failed: " + e.getMessage());
        }
    }
}

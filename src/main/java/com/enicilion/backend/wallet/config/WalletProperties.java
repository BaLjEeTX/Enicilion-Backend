package com.enicilion.backend.wallet.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.wallet")
public class WalletProperties {
    
    private Apple apple = new Apple();
    private Google google = new Google();

    @Data
    public static class Apple {
        private String teamIdentifier = "dummy_team";
        private String passTypeIdentifier = "pass.com.dummy.ticket";
        private String certificatePath = "";
        private String certificatePassword = "";
        private String wwdrCertificatePath = "";
        private boolean mockMode = true;
    }

    @Data
    public static class Google {
        private String issuerId = "dummy_issuer_id";
        private String serviceAccountPath = "";
        private boolean mockMode = true;
    }
}

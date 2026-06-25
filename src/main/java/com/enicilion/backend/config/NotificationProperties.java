package com.enicilion.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "notification")
public class NotificationProperties {

    private WhatsApp whatsapp = new WhatsApp();
    private Email email = new Email();
    private Pdf pdf = new Pdf();

    @Data
    public static class WhatsApp {
        private String token;
        private String phoneNumberId;
        private String templateName;
        private String apiUrl;
    }

    @Data
    public static class Email {
        private String zeptoToken;
        private String zeptoApiUrl;
        private String fromAddress;
        private String fromName;
    }

    @Data
    public static class Pdf {
        private String baseUrl;
    }
}

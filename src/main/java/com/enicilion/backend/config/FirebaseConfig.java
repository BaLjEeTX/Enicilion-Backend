package com.enicilion.backend.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Configuration
@Slf4j
public class FirebaseConfig {

    @Value("${app.firebase.service-account-json:}")
    private String firebaseServiceAccountJson;

    @PostConstruct
    public void initializeFirebase() {
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                if (firebaseServiceAccountJson == null || firebaseServiceAccountJson.trim().isEmpty()) {
                    log.warn("Firebase service account JSON is not configured. Real-time sync will be disabled or mocked.");
                    return;
                }

                InputStream serviceAccount = new ByteArrayInputStream(
                        firebaseServiceAccountJson.getBytes(StandardCharsets.UTF_8)
                );

                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .build();

                FirebaseApp.initializeApp(options);
                log.info("Firebase Application has been initialized successfully.");
            }
        } catch (IOException e) {
            log.error("Failed to initialize Firebase Application", e);
        }
    }
}

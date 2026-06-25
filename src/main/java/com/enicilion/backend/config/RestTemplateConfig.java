package com.enicilion.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.client.RestTemplate;

/**
 * Registers the shared RestTemplate bean used by notification services
 * (EmailNotificationService → ZeptoMail, WhatsAppNotificationService → AiSensy).
 *
 * Also enables Spring's @Async execution model so notification calls
 * dispatched from RazorpayService run on a separate thread pool and
 * never block the HTTP response.
 */
@Configuration
@EnableAsync
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean(name = "notificationExecutor")
    public java.util.concurrent.Executor notificationExecutor() {
        org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor ex =
            new org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor();
        ex.setCorePoolSize(2);
        ex.setMaxPoolSize(5);
        ex.setQueueCapacity(50);
        ex.setThreadNamePrefix("notification-");
        ex.initialize();
        return ex;
    }
}

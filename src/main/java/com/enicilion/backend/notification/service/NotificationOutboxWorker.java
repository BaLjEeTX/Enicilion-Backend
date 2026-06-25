package com.enicilion.backend.notification.service;

import com.enicilion.backend.notification.entity.NotificationOutbox;
import com.enicilion.backend.notification.entity.OutboxStatus;
import com.enicilion.backend.notification.repository.NotificationOutboxRepository;
import com.enicilion.backend.notification.dto.TicketNotificationRequest;
import com.enicilion.backend.tickets.entity.SpectatorTicket;
import com.enicilion.backend.tickets.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationOutboxWorker {

    private final NotificationOutboxRepository outboxRepository;
    private final TicketRepository ticketRepository;
    private final WhatsAppService whatsAppService;
    private final EmailService emailService;

    private static final int BATCH_SIZE = 5;
    private static final int MAX_ATTEMPTS = 3;

    @Scheduled(fixedDelay = 10000) // Runs every 10 seconds
    @Transactional
    public void processOutbox() {
        OffsetDateTime now = OffsetDateTime.now();
        List<NotificationOutbox> pendingTasks = outboxRepository.findPendingTasks(
                OutboxStatus.PENDING, now, MAX_ATTEMPTS, PageRequest.of(0, BATCH_SIZE)
        );

        if (pendingTasks.isEmpty()) {
            return;
        }

        log.info("[OutboxWorker] Processing batch of {} pending tasks", pendingTasks.size());

        for (NotificationOutbox task : pendingTasks) {
            try {
                // Fetch ticket details
                SpectatorTicket ticket = ticketRepository.findByTicketCodeWithDetails(task.getTicketCode()).orElse(null);
                if (ticket == null) {
                    log.warn("[OutboxWorker] Ticket not found: {}", task.getTicketCode());
                    task.setStatus(OutboxStatus.FAILED);
                    task.setErrorMessage("Ticket not found in database");
                    outboxRepository.save(task);
                    continue;
                }

                String email = task.getOverrideEmail() != null && !task.getOverrideEmail().isBlank()
                        ? task.getOverrideEmail()
                        : ticket.getUser().getEmail();

                String phone = task.getOverridePhone() != null && !task.getOverridePhone().isBlank()
                        ? task.getOverridePhone()
                        : ticket.getUser().getWhatsapp();

                TicketNotificationRequest req = TicketNotificationRequest.builder()
                        .userName(ticket.getUser().getFullName())
                        .userEmail(email)
                        .userPhone(phone)
                        .ticketCode(ticket.getTicketCode())
                        .eventName(ticket.getEvent().getName())
                        .eventDate(ticket.getEvent().getEventDate() != null ? ticket.getEvent().getEventDate().toString() : "TBD")
                        .eventLocation(ticket.getEvent().getLocation())
                        .orderId(ticket.getPayment() != null ? ticket.getPayment().getId().toString() : ticket.getTicketCode())
                        .quantity(1)
                        .tierName(ticket.getTier() != null ? ticket.getTier().getName() : "General Admission")
                        .build();

                // Process dispatches
                whatsAppService.sendTicketConfirmed(req);
                emailService.sendTicketConfirmationEmail(req);

                // Mark successful
                task.setStatus(OutboxStatus.SENT);
                task.setAttempts(task.getAttempts() + 1);
                task.setErrorMessage(null);
                outboxRepository.save(task);

            } catch (Exception e) {
                log.error("[OutboxWorker] Error processing outbox task id={} for ticketCode={}: {}",
                        task.getId(), task.getTicketCode(), e.getMessage(), e);

                int attempts = task.getAttempts() + 1;
                task.setAttempts(attempts);
                task.setErrorMessage(e.getMessage());

                if (attempts >= MAX_ATTEMPTS) {
                    task.setStatus(OutboxStatus.FAILED);
                } else {
                    task.setScheduledAt(now.plusMinutes(2)); // Try again in 2 minutes
                }
                outboxRepository.save(task);
            }
        }
    }
}

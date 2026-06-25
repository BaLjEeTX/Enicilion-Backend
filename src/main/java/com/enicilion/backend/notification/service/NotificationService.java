package com.enicilion.backend.notification.service;

import com.enicilion.backend.notification.entity.NotificationOutbox;
import com.enicilion.backend.notification.entity.OutboxStatus;
import com.enicilion.backend.notification.repository.NotificationOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationOutboxRepository outboxRepository;

    /**
     * Queues ticket confirmation task in the database outbox.
     * If called within an active transaction, the outbox record commits atomically when the transaction commits.
     */
    @Transactional
    public void queueTicketConfirmation(String ticketCode, String overrideEmail, String overridePhone) {
        log.info("[Notification] Queueing ticket confirmation outbox task for ticket={}", ticketCode);

        NotificationOutbox task = NotificationOutbox.builder()
                .ticketCode(ticketCode)
                .overrideEmail(overrideEmail)
                .overridePhone(overridePhone)
                .status(OutboxStatus.PENDING)
                .attempts(0)
                .scheduledAt(OffsetDateTime.now())
                .build();

        outboxRepository.save(task);
    }
}

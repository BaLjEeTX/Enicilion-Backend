package com.enicilion.backend.notification.repository;

import com.enicilion.backend.notification.entity.NotificationOutbox;
import com.enicilion.backend.notification.entity.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, UUID> {

    @Query("SELECT n FROM NotificationOutbox n WHERE n.status = :status AND n.scheduledAt <= :now AND n.attempts < :maxAttempts ORDER BY n.scheduledAt ASC")
    List<NotificationOutbox> findPendingTasks(
            @Param("status") OutboxStatus status,
            @Param("now") OffsetDateTime now,
            @Param("maxAttempts") int maxAttempts,
            Pageable pageable
    );
}

package com.enicilion.backend.tickets.repository;

import com.enicilion.backend.tickets.entity.EventSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EventSummaryRepository extends JpaRepository<EventSummary, UUID> {
    Optional<EventSummary> findByEventId(UUID eventId);
}

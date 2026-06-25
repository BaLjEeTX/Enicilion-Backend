package com.enicilion.backend.applications.repository;

import com.enicilion.backend.applications.entity.EventApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApplicationRepository extends JpaRepository<EventApplication, UUID> {
    List<EventApplication> findByUserId(UUID userId);
    List<EventApplication> findByEventId(UUID eventId);
    Optional<EventApplication> findByEventIdAndUserId(UUID eventId, UUID userId);
}

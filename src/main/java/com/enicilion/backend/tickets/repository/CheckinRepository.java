package com.enicilion.backend.tickets.repository;

import com.enicilion.backend.tickets.entity.CheckinEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CheckinRepository extends JpaRepository<CheckinEvent, UUID> {
    int countByTicketCodeAndAction(String ticketCode, String action);
    List<CheckinEvent> findTop50ByOrderByCreatedAtDesc();
}

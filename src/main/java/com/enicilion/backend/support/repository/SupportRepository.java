package com.enicilion.backend.support.repository;

import com.enicilion.backend.support.entity.SupportTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SupportRepository extends JpaRepository<SupportTicket, UUID> {
    List<SupportTicket> findByUserId(UUID userId);
}

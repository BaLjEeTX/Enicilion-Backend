package com.enicilion.backend.influencer.repository;

import com.enicilion.backend.influencer.entity.InfluencerAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InfluencerAuditLogRepository extends JpaRepository<InfluencerAuditLog, UUID> {
    List<InfluencerAuditLog> findAllByOrderByCreatedAtDesc();
}

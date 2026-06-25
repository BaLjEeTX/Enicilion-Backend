package com.enicilion.backend.influencer.repository;

import com.enicilion.backend.influencer.entity.InfluencerApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InfluencerApplicationRepository extends JpaRepository<InfluencerApplication, UUID> {
    Optional<InfluencerApplication> findByUserId(UUID userId);
    List<InfluencerApplication> findAllByOrderByCreatedAtDesc();
}

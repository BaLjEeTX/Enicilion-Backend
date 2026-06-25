package com.enicilion.backend.influencer.repository;

import com.enicilion.backend.influencer.entity.InfluencerPayout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InfluencerPayoutRepository extends JpaRepository<InfluencerPayout, UUID> {
    List<InfluencerPayout> findByInfluencerProfileIdOrderByCreatedAtDesc(UUID profileId);
    List<InfluencerPayout> findAllByOrderByCreatedAtDesc();
}

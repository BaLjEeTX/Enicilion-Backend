package com.enicilion.backend.influencer.repository;

import com.enicilion.backend.influencer.entity.InfluencerProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

@Repository
public interface InfluencerProfileRepository extends JpaRepository<InfluencerProfile, UUID> {
    Optional<InfluencerProfile> findByUserId(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM InfluencerProfile p WHERE p.id = :id")
    Optional<InfluencerProfile> findByIdForUpdate(@Param("id") UUID id);
}

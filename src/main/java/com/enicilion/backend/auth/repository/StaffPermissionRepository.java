package com.enicilion.backend.auth.repository;

import com.enicilion.backend.auth.entity.StaffPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StaffPermissionRepository extends JpaRepository<StaffPermission, UUID> {
    boolean existsByEmailAndFeature(String email, String feature);
    List<StaffPermission> findByEmail(String email);
    Optional<StaffPermission> findByEmailAndFeature(String email, String feature);
}

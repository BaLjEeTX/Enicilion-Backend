package com.enicilion.backend.auth.repository;

import com.enicilion.backend.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    Optional<User> findByReferralCode(String referralCode);
    Optional<User> findByEmailVerificationToken(String emailVerificationToken);
}

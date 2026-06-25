package com.enicilion.backend.auth.service;

import com.enicilion.backend.auth.entity.User;
import com.enicilion.backend.auth.entity.UserRole;
import com.enicilion.backend.auth.repository.UserRepository;
import com.enicilion.backend.common.exception.BadValidationException;
import com.enicilion.backend.common.exception.ResourceNotFoundException;
import com.enicilion.backend.common.exception.UnauthorizedException;
import com.enicilion.backend.tickets.service.EmailNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final com.enicilion.backend.auth.repository.StaffPermissionRepository staffPermissionRepository;
    private final EmailNotificationService emailNotificationService;
    private final ReferralCodeService referralCodeService;
    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    @Transactional
    public Map<String, Object> register(String fullName, String email, String password, String whatsapp, String referredBy) {
        Optional<User> existingUser = userRepository.findByEmail(email);
        User user;
        
        if (existingUser.isPresent()) {
            user = existingUser.get();
            if (user.getPasswordHash() != null && user.getPasswordHash().startsWith("GUEST_")) {
                // Upgrade guest account to fully registered user
                user.setFullName(fullName);
                user.setPasswordHash(passwordEncoder.encode(password));
                user.setWhatsapp(whatsapp);
                if (user.getReferralCode() == null || user.getReferralCode().trim().isEmpty()) {
                    user.setReferralCode(referralCodeService.generateUniqueReferralCode(fullName));
                }
                if (user.getReferredBy() == null || user.getReferredBy().trim().isEmpty()) {
                    user.setReferredBy(referredBy);
                }
                
                // If they have permissions, upgrade them to staff
                if (!staffPermissionRepository.findByEmail(email).isEmpty()) {
                    user.setRole(UserRole.staff);
                }
                
                user.setEmailVerified(false);
                user.setEmailVerificationToken(UUID.randomUUID().toString());
                user.setEmailVerificationExpiry(OffsetDateTime.now().plusHours(24));
                userRepository.save(user);
            } else {
                throw new BadValidationException("Email is already registered");
            }
        } else {
            String referralCode = referralCodeService.generateUniqueReferralCode(fullName);
            UserRole role = UserRole.user;
            if (!staffPermissionRepository.findByEmail(email).isEmpty()) {
                role = UserRole.staff;
            }
            user = User.builder()
                    .fullName(fullName)
                    .email(email)
                    .passwordHash(passwordEncoder.encode(password))
                    .whatsapp(whatsapp)
                    .role(role)
                    .referralCode(referralCode)
                    .referredBy(referredBy)
                    .isEmailVerified(false)
                    .emailVerificationToken(UUID.randomUUID().toString())
                    .emailVerificationExpiry(OffsetDateTime.now().plusHours(24))
                    .build();
            userRepository.save(user);
        }

        // Send email
        emailNotificationService.sendVerificationEmail(user);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Registration successful. Please verify your email address via the link sent to your inbox.");
        return response;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> login(String email, String password) {
        Optional<User> userOpt = userRepository.findByEmail(email);

        // Dummy check to balance time and prevent email enumeration attacks
        String passwordHash = userOpt.map(User::getPasswordHash)
                .orElse("$2a$12$LwPshP1FzL.3D.mN7H4qBuP9fG6x1L6XQyXGZ/Qh4y8Y.YyYyYyYy");
        boolean matches = passwordEncoder.matches(password, passwordHash);

        if (userOpt.isEmpty() || !matches) {
            throw new UnauthorizedException("Invalid email or password");
        }

        User user = userOpt.get();

        if (!user.isEmailVerified()) {
            throw new UnauthorizedException("Email not verified. Please verify your email before logging in.");
        }

        if (user.isBanned()) {
            throw new UnauthorizedException("Your account has been banned");
        }

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        return createAuthResponse(accessToken, refreshToken, user);
    }

    @Transactional
    public Map<String, Object> refresh(String refreshToken) {
        if (refreshToken == null || !jwtService.isRefreshTokenValid(refreshToken)) {
            throw new UnauthorizedException("Invalid or expired refresh token");
        }

        // 1. Check if token is blacklisted in Redis (token reuse detection)
        String blacklistKey = "blacklist:refresh_token:" + refreshToken;
        Boolean isBlacklisted = redisTemplate.hasKey(blacklistKey);
        if (isBlacklisted != null && isBlacklisted) {
            log.warn("Refresh token reuse detected! Token is blacklisted. Revoking sessions for this token.");
            throw new UnauthorizedException("Refresh token reuse detected. Please log in again.");
        }

        String email = jwtService.extractEmailFromRefreshToken(refreshToken);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        if (!user.isEmailVerified()) {
            throw new UnauthorizedException("Email not verified");
        }

        if (user.isBanned()) {
            throw new UnauthorizedException("Your account has been banned");
        }

        // 2. Blacklist the old refresh token in Redis with its remaining TTL
        long remainingMs = jwtService.getRemainingExpirationMsFromRefreshToken(refreshToken);
        if (remainingMs > 0) {
            redisTemplate.opsForValue().set(
                    blacklistKey,
                    "revoked",
                    java.time.Duration.ofMillis(remainingMs)
            );
        }

        // 3. Generate new tokens (RTR)
        String newAccessToken = jwtService.generateAccessToken(user);
        String newRefreshToken = jwtService.generateRefreshToken(user);

        Map<String, Object> data = new HashMap<>();
        data.put("access_token", newAccessToken);
        data.put("refresh_token", newRefreshToken);
        return data;
    }

    @Transactional
    public void verifyEmail(String token) {
        User user = userRepository.findByEmailVerificationToken(token)
                .orElseThrow(() -> new BadValidationException("Invalid or expired verification token"));

        if (user.getEmailVerificationExpiry().isBefore(OffsetDateTime.now())) {
            throw new BadValidationException("Verification token has expired");
        }

        user.setEmailVerified(true);
        user.setEmailVerificationToken(null);
        user.setEmailVerificationExpiry(null);
        userRepository.save(user);
    }

    @Transactional
    public void resendVerificationEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.isEmailVerified()) {
            throw new BadValidationException("Email is already verified");
        }

        user.setEmailVerificationToken(UUID.randomUUID().toString());
        user.setEmailVerificationExpiry(OffsetDateTime.now().plusHours(24));
        userRepository.save(user);

        emailNotificationService.sendVerificationEmail(user);
    }



    private Map<String, Object> createAuthResponse(String accessToken, String refreshToken, User user) {
        Map<String, Object> data = new HashMap<>();
        data.put("access_token", accessToken);
        data.put("refresh_token", refreshToken);

        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", user.getId().toString());
        userMap.put("fullName", user.getFullName());
        userMap.put("email", user.getEmail());
        userMap.put("role", user.getRole().name());
        userMap.put("whatsapp", user.getWhatsapp());
        data.put("user", userMap);

        return data;
    }
}

package com.enicilion.backend.auth.service;

import com.enicilion.backend.auth.entity.StaffPermission;
import com.enicilion.backend.auth.entity.User;
import com.enicilion.backend.auth.entity.UserRole;
import com.enicilion.backend.auth.repository.StaffPermissionRepository;
import com.enicilion.backend.common.exception.BadValidationException;
import com.enicilion.backend.common.exception.ResourceNotFoundException;
import com.enicilion.backend.common.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class StaffPermissionService {

    private final StaffPermissionRepository repository;
    private final com.enicilion.backend.auth.repository.UserRepository userRepository;

    public void checkPermission(User user, String feature) {
        if (user.getRole() == UserRole.admin) {
            return; // Admin always bypasses checks and has access
        }
        if (user.getRole() != UserRole.staff) {
            throw new UnauthorizedException("Access denied. Admin or Staff privileges required.");
        }
        if (!repository.existsByEmailAndFeature(user.getEmail(), feature)) {
            throw new UnauthorizedException("Access denied. You do not have permission for feature: " + feature);
        }
    }

    @Transactional
    public StaffPermission grantPermission(String email, String feature) {
        if (repository.existsByEmailAndFeature(email, feature)) {
            throw new BadValidationException("Permission for feature " + feature + " has already been granted to " + email);
        }
        
        StaffPermission permission = StaffPermission.builder()
                .email(email)
                .feature(feature)
                .build();
                
        log.info("Granting feature permission: {} to staff: {}", feature, email);
        
        // Automatically upgrade existing user's role to staff
        userRepository.findByEmail(email).ifPresent(u -> {
            if (u.getRole() == UserRole.user) {
                u.setRole(UserRole.staff);
                userRepository.save(u);
                log.info("Automatically upgraded user {} to ROLE_staff upon permission grant", email);
            }
        });

        return repository.save(permission);
    }

    @Transactional
    public void revokePermission(String email, String feature) {
        StaffPermission permission = repository.findByEmailAndFeature(email, feature)
                .orElseThrow(() -> new ResourceNotFoundException("Permission not found for staff email and feature: " + email + ", " + feature));
                
        log.info("Revoking feature permission: {} from staff: {}", feature, email);
        repository.delete(permission);

        // Automatically demote user back to regular user if no permissions remain
        List<StaffPermission> remaining = repository.findByEmail(email);
        if (remaining.isEmpty()) {
            userRepository.findByEmail(email).ifPresent(u -> {
                if (u.getRole() == UserRole.staff) {
                    u.setRole(UserRole.user);
                    userRepository.save(u);
                    log.info("Automatically demoted user {} to ROLE_user as all permissions were revoked", email);
                }
            });
        }
    }

    @Transactional(readOnly = true)
    public List<StaffPermission> getPermissionsByEmail(String email) {
        return repository.findByEmail(email);
    }

    @Transactional(readOnly = true)
    public List<StaffPermission> getAllPermissions() {
        return repository.findAll();
    }
}

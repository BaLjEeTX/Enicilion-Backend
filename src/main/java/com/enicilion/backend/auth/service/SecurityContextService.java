package com.enicilion.backend.auth.service;

import com.enicilion.backend.auth.entity.User;
import com.enicilion.backend.auth.entity.UserRole;
import com.enicilion.backend.auth.repository.UserRepository;
import com.enicilion.backend.common.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Centralized service for retrieving the authenticated user from the
 * SecurityContext and performing role-based authorization checks.
 * <p>
 * Eliminates duplicated {@code getCurrentUser()}, {@code checkAdminOrStaff()},
 * and {@code checkAdminOnly()} methods across controllers.
 */
@Service
@RequiredArgsConstructor
public class SecurityContextService {

    private final UserRepository userRepository;

    /**
     * Retrieves the currently authenticated user from the SecurityContext.
     *
     * @return the authenticated {@link User} entity
     * @throws UnauthorizedException if no authenticated user is found
     */
    public User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Authenticated user not found."));
    }

    /**
     * Ensures the given user has admin or staff role.
     *
     * @param user the user to check
     * @throws UnauthorizedException if the user lacks the required role
     */
    public void checkAdminOrStaff(User user) {
        if (user.getRole() != UserRole.admin && user.getRole() != UserRole.staff) {
            throw new UnauthorizedException("Access denied. Admin or Staff privileges required.");
        }
    }

    /**
     * Ensures the given user has the admin role.
     *
     * @param user the user to check
     * @throws UnauthorizedException if the user is not an admin
     */
    public void checkAdminOnly(User user) {
        if (user.getRole() != UserRole.admin) {
            throw new UnauthorizedException("Access denied. Admin privileges required.");
        }
    }
}

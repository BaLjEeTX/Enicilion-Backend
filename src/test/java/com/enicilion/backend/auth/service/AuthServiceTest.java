package com.enicilion.backend.auth.service;

import com.enicilion.backend.auth.entity.User;
import com.enicilion.backend.auth.entity.UserRole;
import com.enicilion.backend.auth.repository.StaffPermissionRepository;
import com.enicilion.backend.auth.repository.UserRepository;
import com.enicilion.backend.common.exception.BadValidationException;
import com.enicilion.backend.common.exception.ResourceNotFoundException;
import com.enicilion.backend.common.exception.UnauthorizedException;
import com.enicilion.backend.tickets.service.EmailNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private StaffPermissionRepository staffPermissionRepository;

    @Mock
    private EmailNotificationService emailNotificationService;

    @Mock
    private ReferralCodeService referralCodeService;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testRegisterNewUserGeneratesUnverifiedStateAndToken() {
        String email = "newuser@test.com";
        String password = "password123";
        String fullName = "New User";
        String whatsapp = "1234567890";
        String referredBy = "";

        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());
        when(userRepository.findByReferralCode(anyString())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(password)).thenReturn("hashed_password");
        when(staffPermissionRepository.findByEmail(email)).thenReturn(Collections.emptyList());
        when(referralCodeService.generateUniqueReferralCode(anyString())).thenReturn("NEWUS1234");

        Map<String, Object> response = authService.register(fullName, email, password, whatsapp, referredBy);

        assertNotNull(response);
        assertTrue(response.containsKey("message"));
        assertTrue(response.get("message").toString().contains("verify your email"));

        // Capture saved User and verify fields
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();

        assertNotNull(savedUser);
        assertEquals(fullName, savedUser.getFullName());
        assertEquals(email, savedUser.getEmail());
        assertEquals("hashed_password", savedUser.getPasswordHash());
        assertEquals(whatsapp, savedUser.getWhatsapp());
        assertFalse(savedUser.isEmailVerified());
        assertNotNull(savedUser.getEmailVerificationToken());
        assertNotNull(savedUser.getEmailVerificationExpiry());
        assertTrue(savedUser.getEmailVerificationExpiry().isAfter(OffsetDateTime.now()));

        verify(emailNotificationService, times(1)).sendVerificationEmail(savedUser);
    }

    @Test
    void testLoginBlocksUnverifiedUser() {
        String email = "unverified@test.com";
        String password = "password123";

        User user = User.builder()
                .email(email)
                .passwordHash("hashed_password")
                .isEmailVerified(false)
                .build();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(password, "hashed_password")).thenReturn(true);

        UnauthorizedException exception = assertThrows(UnauthorizedException.class, () -> {
            authService.login(email, password);
        });

        assertEquals("Email not verified. Please verify your email before logging in.", exception.getMessage());
    }

    @Test
    void testLoginAllowsVerifiedUser() {
        String email = "verified@test.com";
        String password = "password123";

        User user = User.builder()
                .id(java.util.UUID.randomUUID())
                .email(email)
                .passwordHash("hashed_password")
                .fullName("Verified User")
                .role(UserRole.user)
                .whatsapp("12345")
                .isEmailVerified(true)
                .isBanned(false)
                .build();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(password, "hashed_password")).thenReturn(true);
        when(jwtService.generateAccessToken(user)).thenReturn("access_token_xyz");
        when(jwtService.generateRefreshToken(user)).thenReturn("refresh_token_abc");

        Map<String, Object> response = authService.login(email, password);

        assertNotNull(response);
        assertEquals("access_token_xyz", response.get("access_token"));
        assertEquals("refresh_token_abc", response.get("refresh_token"));
        assertNotNull(response.get("user"));
    }

    @Test
    void testLoginBlocksBannedUserEvenIfVerified() {
        String email = "banned@test.com";
        String password = "password123";

        User user = User.builder()
                .email(email)
                .passwordHash("hashed_password")
                .isEmailVerified(true)
                .isBanned(true)
                .build();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(password, "hashed_password")).thenReturn(true);

        UnauthorizedException exception = assertThrows(UnauthorizedException.class, () -> {
            authService.login(email, password);
        });

        assertEquals("Your account has been banned", exception.getMessage());
    }

    @Test
    void testVerifyEmailSuccess() {
        String token = "valid-token-123";
        User user = User.builder()
                .email("user@test.com")
                .isEmailVerified(false)
                .emailVerificationToken(token)
                .emailVerificationExpiry(OffsetDateTime.now().plusHours(2))
                .build();

        when(userRepository.findByEmailVerificationToken(token)).thenReturn(Optional.of(user));

        assertDoesNotThrow(() -> authService.verifyEmail(token));

        assertTrue(user.isEmailVerified());
        assertNull(user.getEmailVerificationToken());
        assertNull(user.getEmailVerificationExpiry());
        verify(userRepository, times(1)).save(user);
    }

    @Test
    void testVerifyEmailInvalidTokenThrowsException() {
        String token = "invalid-token";
        when(userRepository.findByEmailVerificationToken(token)).thenReturn(Optional.empty());

        BadValidationException exception = assertThrows(BadValidationException.class, () -> {
            authService.verifyEmail(token);
        });

        assertEquals("Invalid or expired verification token", exception.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testVerifyEmailExpiredTokenThrowsException() {
        String token = "expired-token";
        User user = User.builder()
                .email("user@test.com")
                .isEmailVerified(false)
                .emailVerificationToken(token)
                .emailVerificationExpiry(OffsetDateTime.now().minusHours(1))
                .build();

        when(userRepository.findByEmailVerificationToken(token)).thenReturn(Optional.of(user));

        BadValidationException exception = assertThrows(BadValidationException.class, () -> {
            authService.verifyEmail(token);
        });

        assertEquals("Verification token has expired", exception.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testResendVerificationEmailSuccess() {
        String email = "unverified@test.com";
        User user = User.builder()
                .email(email)
                .isEmailVerified(false)
                .build();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        assertDoesNotThrow(() -> authService.resendVerificationEmail(email));

        assertNotNull(user.getEmailVerificationToken());
        assertNotNull(user.getEmailVerificationExpiry());
        assertTrue(user.getEmailVerificationExpiry().isAfter(OffsetDateTime.now()));
        verify(userRepository, times(1)).save(user);
        verify(emailNotificationService, times(1)).sendVerificationEmail(user);
    }

    @Test
    void testResendVerificationEmailAlreadyVerifiedThrowsException() {
        String email = "verified@test.com";
        User user = User.builder()
                .email(email)
                .isEmailVerified(true)
                .build();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        BadValidationException exception = assertThrows(BadValidationException.class, () -> {
            authService.resendVerificationEmail(email);
        });

        assertEquals("Email is already verified", exception.getMessage());
        verify(userRepository, never()).save(any(User.class));
        verify(emailNotificationService, never()).sendVerificationEmail(any(User.class));
    }

    @Test
    void testResendVerificationEmailUserNotFoundThrowsException() {
        String email = "nonexistent@test.com";
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> {
            authService.resendVerificationEmail(email);
        });

        assertEquals("User not found", exception.getMessage());
    }
}

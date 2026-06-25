package com.enicilion.backend.auth.controller;

import com.enicilion.backend.auth.dto.LoginRequest;
import com.enicilion.backend.auth.dto.RegisterRequest;
import com.enicilion.backend.auth.service.AuthService;
import com.enicilion.backend.common.dto.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Map<String, Object>>> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletResponse response) {
        
        Map<String, Object> result = authService.register(
                request.getFullName(),
                request.getEmail(),
                request.getPassword(),
                request.getWhatsapp(),
                request.getReferredBy()
        );

        String refreshToken = (String) result.remove("refresh_token");
        if (refreshToken != null) {
            setRefreshTokenCookie(response, refreshToken);
        } else {
            setRefreshTokenCookie(response, "");
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(result));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<ApiResponse<Map<String, Object>>> verifyEmail(
            @RequestParam("token") String token) {
        authService.verifyEmail(token);
        Map<String, Object> data = new HashMap<>();
        data.put("message", "Email verified successfully");
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<ApiResponse<Map<String, Object>>> resendVerification(
            @RequestParam("email") String email) {
        authService.resendVerificationEmail(email);
        Map<String, Object> data = new HashMap<>();
        data.put("message", "Verification email resent successfully");
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {
        
        Map<String, Object> result = authService.login(
                request.getEmail(),
                request.getPassword()
        );

        String refreshToken = (String) result.remove("refresh_token");
        setRefreshTokenCookie(response, refreshToken);

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Map<String, Object>>> refresh(
            @CookieValue(name = "refresh_token", required = false) String refreshToken,
            HttpServletResponse response) {
        
        Map<String, Object> result = authService.refresh(refreshToken);
        String newRefreshToken = (String) result.remove("refresh_token");
        if (newRefreshToken != null) {
            setRefreshTokenCookie(response, newRefreshToken);
        }
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletResponse response) {
        setRefreshTokenCookie(response, "");
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    private void setRefreshTokenCookie(HttpServletResponse response, String token) {
        String cookieToken = token != null ? token : "";
        ResponseCookie cookie = ResponseCookie.from("refresh_token", cookieToken)
                .httpOnly(true)
                .secure(true) // In production, must be true. For localhost HTTP, some browsers need secure=false unless testing on https/localhost
                .path("/")
                .maxAge(cookieToken.isEmpty() ? 0 : 7 * 24 * 60 * 60)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}

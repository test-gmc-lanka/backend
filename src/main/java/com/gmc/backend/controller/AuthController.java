package com.gmc.backend.controller;

import java.util.Locale;
import java.time.Duration;
import jakarta.servlet.http.HttpServletRequest;
import com.gmc.backend.security.RateLimiter;
import com.gmc.backend.dto.request.ForgotPasswordRequest;
import com.gmc.backend.dto.request.LoginRequest;
import com.gmc.backend.dto.request.RegisterRequest;
import com.gmc.backend.dto.request.ResetPasswordRequest;
import com.gmc.backend.dto.response.AuthResponse;
import com.gmc.backend.service.AuthService;
import com.gmc.backend.service.PasswordResetService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final RateLimiter rateLimiter;

    private static final Duration FIFTEEN_MINUTES = Duration.ofMinutes(15);
    private static final Duration ONE_HOUR = Duration.ofHours(1);

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest http) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        rateLimiter.check("forgot:email:" + email, 3, ONE_HOUR,
                "Too many reset requests for this email. Please try again in an hour.");
        rateLimiter.check("forgot:ip:" + http.getRemoteAddr(), 20, ONE_HOUR,
                "Too many reset requests. Please try again later.");
        passwordResetService.requestReset(email);
        // Always the same reply, whether or not the email has an account
        return ResponseEntity.ok(Map.of("message",
                "If an account exists for that email, a password reset link has been sent."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request,
                                                 HttpServletRequest http) {
        rateLimiter.check("register:ip:" + http.getRemoteAddr(), 10, ONE_HOUR,
                "Too many sign-ups from this network. Please try again later.");
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest http,
            HttpServletResponse response) {
        String email = request.getEmail() == null ? "" : request.getEmail().trim().toLowerCase(Locale.ROOT);
        rateLimiter.check("login:" + http.getRemoteAddr() + ":" + email, 10, FIFTEEN_MINUTES,
                "Too many login attempts. Please wait 15 minutes and try again.");
        rateLimiter.check("login:ip:" + http.getRemoteAddr(), 50, FIFTEEN_MINUTES,
                "Too many login attempts from this network. Please try again later.");
        return ResponseEntity.ok(authService.login(request, response));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(value = "refreshToken", required = false) String refreshToken,
            HttpServletResponse response) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(authService.refresh(refreshToken, response));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(value = "refreshToken", required = false) String refreshToken,
            HttpServletResponse response) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            authService.logout(refreshToken, response);
        }
        return ResponseEntity.noContent().build();
    }
}

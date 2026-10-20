package com.gmc.backend.service.impl;

import com.gmc.backend.dto.request.LoginRequest;
import com.gmc.backend.dto.request.RegisterRequest;
import com.gmc.backend.dto.response.AuthResponse;
import com.gmc.backend.exception.DuplicateEmailException;
import com.gmc.backend.model.Role;
import com.gmc.backend.model.User;
import com.gmc.backend.repository.RoleRepository;
import com.gmc.backend.repository.UserRepository;
import com.gmc.backend.security.CustomUserDetails;
import com.gmc.backend.security.JwtTokenProvider;
import com.gmc.backend.service.AuthService;
import com.gmc.backend.service.RefreshTokenResult;
import com.gmc.backend.service.RefreshTokenService;
import jakarta.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final int REFRESH_COOKIE_MAX_AGE = 7 * 24 * 60 * 60;
    private static final String REFRESH_COOKIE = "refreshToken";

    @Value("${app.cookie.secure:false}")
    private boolean secureCookies;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.getEmail());
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateEmailException(email);
        }

        Role defaultRole = roleRepository
                .findByRoleName("CUSTOMER")
                .orElseGet(() ->
                        roleRepository.save(Role.builder().roleName("CUSTOMER").build()));

        User user = User.builder()
                .name(request.getName().trim())
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .role(defaultRole)
                .build();

        User saved = userRepository.save(user);

        return buildAuthResponse(saved);
    }

    @Override
    public AuthResponse login(LoginRequest request, HttpServletResponse response) {
        String email = normalizeEmail(request.getEmail());
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, request.getPassword()));

        User user = userRepository.findByEmail(email).orElseThrow(() -> new UsernameNotFoundException(email));

        String rawToken = refreshTokenService.createRefreshToken(user.getUserId());
        addRefreshCookie(response, rawToken);

        return buildAuthResponse(user);
    }

    @Override
    public AuthResponse refresh(String rawRefreshToken, HttpServletResponse response) {
        RefreshTokenResult result = refreshTokenService.rotateRefreshToken(rawRefreshToken);
        addRefreshCookie(response, result.rawToken());
        return buildAuthResponse(result.user());
    }

    @Override
    public void logout(String rawRefreshToken, HttpServletResponse response) {
        refreshTokenService.invalidateByRawToken(rawRefreshToken);
        clearRefreshCookie(response);
    }

    private AuthResponse buildAuthResponse(User user) {
        CustomUserDetails userDetails = new CustomUserDetails(user);
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", user.getRole().getRoleName());
        claims.put("name", user.getName());
        claims.put("userId", user.getUserId());
        String accessToken = jwtTokenProvider.generateToken(userDetails, claims);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .role(user.getRole().getRoleName())
                .name(user.getName())
                .userId(user.getUserId())
                .email(user.getEmail())
                .profileImageUrl(user.getProfileImageUrl())
                .build();
    }

    @Override
    public void startNewSession(Long userId, HttpServletResponse response) {
        addRefreshCookie(response, refreshTokenService.createRefreshToken(userId));
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    // HttpOnly (no JS access), SameSite=Strict (not sent cross-site), only sent to /api/auth,
    // Secure when app.cookie.secure=true (HTTPS in production)
    private void addRefreshCookie(HttpServletResponse response, String rawToken) {
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                refreshCookie(rawToken, REFRESH_COOKIE_MAX_AGE).toString());
        // Remove the cookie older versions set on path "/"
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                ResponseCookie.from(REFRESH_COOKIE, "")
                        .path("/")
                        .maxAge(0)
                        .httpOnly(true)
                        .build()
                        .toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie("", 0).toString());
    }

    private ResponseCookie refreshCookie(String value, long maxAgeSeconds) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(secureCookies)
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(maxAgeSeconds)
                .build();
    }
}

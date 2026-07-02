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
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final int REFRESH_COOKIE_MAX_AGE = 7 * 24 * 60 * 60;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;

    @Override
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateEmailException(request.getEmail());
        }

        Role defaultRole = roleRepository.findByRoleName("CUSTOMER")
                .orElseGet(() -> roleRepository.save(
                        Role.builder().roleName("CUSTOMER").build()));

        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(defaultRole)
                .build();

        User saved = userRepository.save(user);

        return buildAuthResponse(saved);
    }

    @Override
    public AuthResponse login(LoginRequest request, HttpServletResponse response) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(), request.getPassword()));

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UsernameNotFoundException(request.getEmail()));

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
                .build();
    }

    private void addRefreshCookie(HttpServletResponse response, String rawToken) {
        Cookie cookie = new Cookie("refreshToken", rawToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(false); // set true in production (HTTPS only)
        cookie.setPath("/");
        cookie.setMaxAge(REFRESH_COOKIE_MAX_AGE);
        response.addCookie(cookie);
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie("refreshToken", "");
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }
}

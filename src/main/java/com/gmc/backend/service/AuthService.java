package com.gmc.backend.service;

import com.gmc.backend.dto.request.LoginRequest;
import com.gmc.backend.dto.request.RegisterRequest;
import com.gmc.backend.dto.response.AuthResponse;
import com.gmc.backend.model.RefreshToken;
import com.gmc.backend.model.Role;
import com.gmc.backend.model.User;
import com.gmc.backend.repository.RoleRepository;
import com.gmc.backend.repository.UserRepository;
import com.gmc.backend.security.CustomUserDetails;
import com.gmc.backend.security.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.CookieValue;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already in use");
        }

        Role defaultRole = roleRepository.findByRoleName("CUSTOMER")
                .orElseGet(() -> roleRepository.save(Role.builder().roleName("CUSTOMER").build()));

        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(defaultRole) // default role
                .build();

        User saved = userRepository.save(user);

        CustomUserDetails userDetails = new CustomUserDetails(saved);
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("role", saved.getRole().getRoleName());
        extraClaims.put("name", saved.getName());
        extraClaims.put("userId", saved.getUserId());

        String token = jwtTokenProvider.generateToken(userDetails, extraClaims);

        return AuthResponse.builder()
                .accessToken(token)
                .role(saved.getRole().getRoleName())
                .name(saved.getName())
                .userId(saved.getUserId())
                .build();
    }

    public AuthResponse login(LoginRequest request, HttpServletResponse response) {

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        CustomUserDetails userDetails = new CustomUserDetails(user);

        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("role", user.getRole().getRoleName());
        extraClaims.put("name", user.getName());
        extraClaims.put("userId", user.getUserId());

        String accessToken = jwtTokenProvider.generateToken(userDetails, extraClaims);

        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.getUserId());

        Cookie cookie = new Cookie("refreshToken", refreshToken.getToken());
        cookie.setHttpOnly(true);
        cookie.setSecure(false); // set true in production (HTTPS)
        cookie.setPath("/");
        cookie.setMaxAge(7 * 24 * 60 * 60); // 7 days
        response.addCookie(cookie);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .role(user.getRole().getRoleName())
                .name(user.getName())
                .userId(user.getUserId())
                .build();
    }

    public AuthResponse refresh(@CookieValue("refreshToken") String refreshToken) {

        RefreshToken token = refreshTokenService.findByToken(refreshToken)
                .map(refreshTokenService::verifyExpiration)
                .orElseThrow(() -> new RuntimeException("Invalid refresh token"));

        User user = token.getUser();
        CustomUserDetails userDetails = new CustomUserDetails(user);

        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("role", user.getRole().getRoleName());
        extraClaims.put("name", user.getName());
        extraClaims.put("userId", user.getUserId());

        String newAccessToken = jwtTokenProvider.generateToken(userDetails, extraClaims);

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .role(user.getRole().getRoleName())
                .name(user.getName())
                .userId(user.getUserId())
                .build();
    }

}


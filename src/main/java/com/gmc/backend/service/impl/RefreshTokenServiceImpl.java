package com.gmc.backend.service.impl;

import com.gmc.backend.exception.InvalidTokenException;
import com.gmc.backend.exception.SecurityBreachException;
import com.gmc.backend.model.RefreshToken;
import com.gmc.backend.model.User;
import com.gmc.backend.repository.RefreshTokenRepository;
import com.gmc.backend.repository.UserRepository;
import com.gmc.backend.service.RefreshTokenResult;
import com.gmc.backend.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    @Value("${app.refresh-token-days:7}")
    private long refreshTokenDays;

    @Override
    @Transactional
    public String createRefreshToken(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        refreshTokenRepository.deleteAllByUser(user);

        String rawToken = generateRawToken();

        RefreshToken entity = RefreshToken.builder()
                .user(user)
                .token(hashToken(rawToken))
                .used(false)
                .expiryDate(LocalDateTime.now().plusDays(refreshTokenDays))
                .build();

        refreshTokenRepository.save(entity);
        return rawToken;
    }

    @Override
    @Transactional
    public RefreshTokenResult rotateRefreshToken(String rawToken) {
        String hashed = hashToken(rawToken);

        RefreshToken existing = refreshTokenRepository.findByToken(hashed)
                .orElseThrow(() -> new InvalidTokenException(
                        "REFRESH_TOKEN_INVALID", "Refresh token not found"));

        if (existing.isUsed()) {
            refreshTokenRepository.deleteAllByUser(existing.getUser());
            throw new SecurityBreachException();
        }

        if (existing.getExpiryDate().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.delete(existing);
            throw new InvalidTokenException(
                    "REFRESH_TOKEN_EXPIRED", "Refresh token has expired");
        }

        existing.setUsed(true);
        refreshTokenRepository.save(existing);

        String newRawToken = generateRawToken();
        RefreshToken newEntity = RefreshToken.builder()
                .user(existing.getUser())
                .token(hashToken(newRawToken))
                .used(false)
                .expiryDate(LocalDateTime.now().plusDays(refreshTokenDays))
                .build();

        refreshTokenRepository.save(newEntity);
        return new RefreshTokenResult(newRawToken, existing.getUser());
    }

    @Override
    @Transactional
    public void invalidateByRawToken(String rawToken) {
        String hashed = hashToken(rawToken);
        refreshTokenRepository.findByToken(hashed).ifPresent(token -> {
            token.setUsed(true);
            refreshTokenRepository.save(token);
        });
    }

    private String generateRawToken() {
        byte[] bytes = new byte[64];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm not available", ex);
        }
    }
}

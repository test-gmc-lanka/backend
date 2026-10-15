package com.gmc.backend.service.impl;

import com.gmc.backend.exception.BusinessRuleException;
import com.gmc.backend.model.PasswordResetToken;
import com.gmc.backend.model.User;
import com.gmc.backend.repository.PasswordResetTokenRepository;
import com.gmc.backend.repository.RefreshTokenRepository;
import com.gmc.backend.repository.UserRepository;
import com.gmc.backend.service.EmailService;
import com.gmc.backend.service.PasswordResetService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class PasswordResetServiceImpl implements PasswordResetService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String INVALID_LINK =
            "This reset link is invalid or has expired. Please request a new one.";

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${app.password-reset.token-minutes:30}")
    private long tokenMinutes;

    @Override
    @Transactional
    public void requestReset(String email) {
        // Same outcome for unknown/inactive addresses so the endpoint can't reveal who has an account
        User user = userRepository.findByEmail(email.trim()).orElse(null);
        if (user == null || Boolean.FALSE.equals(user.getActive())) {
            return;
        }
        // Only the newest link works
        resetTokenRepository.deleteAllByUser(user);

        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        resetTokenRepository.save(PasswordResetToken.builder()
                .user(user)
                .token(hash(rawToken))
                .expiryDate(LocalDateTime.now().plusMinutes(tokenMinutes))
                .build());

        String link = frontendUrl.replaceAll("/+$", "") + "/reset-password?token=" + rawToken;
        emailService.send(user.getEmail(), "Reset your GMC Lanka password",
                "Hi " + user.getName() + ",\n\n"
                        + "We received a request to reset your GMC Lanka password. "
                        + "Open the link below to choose a new one:\n\n"
                        + link + "\n\n"
                        + "This link expires in " + tokenMinutes + " minutes and can only be used once.\n"
                        + "If you didn't ask for this, you can ignore this email — "
                        + "your password won't change.\n\n"
                        + "— GMC Lanka");
    }

    @Override
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        PasswordResetToken token = resetTokenRepository.findByToken(hash(rawToken))
                .orElseThrow(() -> new BusinessRuleException(INVALID_LINK));
        if (token.isUsed() || token.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new BusinessRuleException(INVALID_LINK);
        }
        User user = token.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        token.setUsed(true);
        resetTokenRepository.save(token);
        // Sign out every existing session, in case the old password was compromised
        refreshTokenRepository.deleteAllByUser(user);
    }

    private static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}

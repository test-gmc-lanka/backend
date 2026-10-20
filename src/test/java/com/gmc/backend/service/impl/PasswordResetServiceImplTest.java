package com.gmc.backend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gmc.backend.exception.BusinessRuleException;
import com.gmc.backend.model.PasswordResetToken;
import com.gmc.backend.model.User;
import com.gmc.backend.repository.PasswordResetTokenRepository;
import com.gmc.backend.repository.RefreshTokenRepository;
import com.gmc.backend.repository.UserRepository;
import com.gmc.backend.service.EmailService;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetTokenRepository resetTokenRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private PasswordResetServiceImpl passwordResetService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(passwordResetService, "frontendUrl", "http://localhost:5173/");
        ReflectionTestUtils.setField(passwordResetService, "tokenMinutes", 30L);
    }

    private User user(boolean active) {
        return User.builder()
                .userId(1L)
                .name("Alice")
                .email("alice@example.com")
                .password("old-hash")
                .active(active)
                .build();
    }

    // Requests a reset and returns the raw token from the emailed link
    private String requestAndCaptureRawToken(User user) {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        passwordResetService.requestReset(" alice@example.com ");
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService).send(eq("alice@example.com"), anyString(), body.capture());
        Matcher m = Pattern.compile("/reset-password\\?token=([A-Za-z0-9_-]+)").matcher(body.getValue());
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    @Test
    void requestReset_knownUser_storesHashedTokenAndEmailsLink() {
        User user = user(true);
        String rawToken = requestAndCaptureRawToken(user);

        ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(resetTokenRepository).deleteAllByUser(user);
        verify(resetTokenRepository).save(saved.capture());
        assertThat(saved.getValue().getToken()).hasSize(64).isNotEqualTo(rawToken);
        assertThat(saved.getValue().getExpiryDate())
                .isBetween(
                        LocalDateTime.now().plusMinutes(29), LocalDateTime.now().plusMinutes(31));
    }

    @Test
    void requestReset_linkHasNoDoubleSlash() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user(true)));

        passwordResetService.requestReset("alice@example.com");

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService).send(anyString(), anyString(), body.capture());
        assertThat(body.getValue()).contains("http://localhost:5173/reset-password?token=");
    }

    @Test
    void requestReset_unknownEmail_doesNothing() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        passwordResetService.requestReset("nobody@example.com");

        verifyNoInteractions(resetTokenRepository, emailService);
    }

    @Test
    void requestReset_deactivatedUser_doesNothing() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user(false)));

        passwordResetService.requestReset("alice@example.com");

        verifyNoInteractions(resetTokenRepository, emailService);
    }

    @Test
    void resetPassword_validToken_updatesPasswordAndSignsOutSessions() {
        User user = user(true);
        String rawToken = requestAndCaptureRawToken(user);
        ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(resetTokenRepository).save(saved.capture());
        PasswordResetToken stored = saved.getValue();
        when(resetTokenRepository.findByToken(stored.getToken())).thenReturn(Optional.of(stored));
        when(passwordEncoder.encode("newSecret1")).thenReturn("new-hash");

        passwordResetService.resetPassword(rawToken, "newSecret1");

        assertThat(user.getPassword()).isEqualTo("new-hash");
        assertThat(stored.isUsed()).isTrue();
        verify(refreshTokenRepository).deleteAllByUser(user);
    }

    @Test
    void resetPassword_unknownToken_throws() {
        when(resetTokenRepository.findByToken(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> passwordResetService.resetPassword("bogus", "newSecret1"))
                .isInstanceOf(BusinessRuleException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPassword_expiredToken_throws() {
        PasswordResetToken expired = PasswordResetToken.builder()
                .user(user(true))
                .token("h")
                .expiryDate(LocalDateTime.now().minusMinutes(1))
                .build();
        when(resetTokenRepository.findByToken(anyString())).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> passwordResetService.resetPassword("raw", "newSecret1"))
                .isInstanceOf(BusinessRuleException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPassword_usedToken_throws() {
        PasswordResetToken used = PasswordResetToken.builder()
                .user(user(true))
                .token("h")
                .expiryDate(LocalDateTime.now().plusMinutes(5))
                .used(true)
                .build();
        when(resetTokenRepository.findByToken(anyString())).thenReturn(Optional.of(used));

        assertThatThrownBy(() -> passwordResetService.resetPassword("raw", "newSecret1"))
                .isInstanceOf(BusinessRuleException.class);
    }
}

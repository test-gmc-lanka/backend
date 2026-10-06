package com.gmc.backend.service.impl;

import com.gmc.backend.dto.request.LoginRequest;
import com.gmc.backend.dto.request.RegisterRequest;
import com.gmc.backend.dto.response.AuthResponse;
import com.gmc.backend.exception.DuplicateEmailException;
import com.gmc.backend.model.Role;
import com.gmc.backend.model.User;
import com.gmc.backend.repository.RoleRepository;
import com.gmc.backend.repository.UserRepository;
import com.gmc.backend.security.JwtTokenProvider;
import com.gmc.backend.service.RefreshTokenService;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private AuthServiceImpl authService;

    private Role customerRole() {
        return Role.builder().roleId(1L).roleName("CUSTOMER").build();
    }

    @Test
    void register_shouldThrowWhenEmailAlreadyExists() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("existing@example.com");
        req.setName("Test");
        req.setPassword("password");
        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

        assertThrows(DuplicateEmailException.class, () -> authService.register(req));
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_shouldCreateUserWithCustomerRoleAndReturnToken() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("new@example.com");
        req.setName("NewUser");
        req.setPassword("password123");

        Role role = customerRole();
        User saved = User.builder().userId(1L).name("NewUser").email("new@example.com")
                .password("encoded").role(role).build();

        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(roleRepository.findByRoleName("CUSTOMER")).thenReturn(Optional.of(role));
        when(passwordEncoder.encode("password123")).thenReturn("encoded");
        when(userRepository.save(any())).thenReturn(saved);
        when(jwtTokenProvider.generateToken(any(), any())).thenReturn("jwt-token");

        AuthResponse result = authService.register(req);

        assertNotNull(result);
        assertEquals("jwt-token", result.getAccessToken());
        assertEquals("CUSTOMER", result.getRole());
        assertEquals("NewUser", result.getName());
    }

    @Test
    void register_shouldCreateCustomerRoleWhenItDoesNotExist() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("new@example.com");
        req.setName("NewUser");
        req.setPassword("password");

        Role role = customerRole();
        User saved = User.builder().userId(1L).name("NewUser").email("new@example.com")
                .password("encoded").role(role).build();

        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(roleRepository.findByRoleName("CUSTOMER")).thenReturn(Optional.empty());
        when(roleRepository.save(any())).thenReturn(role);
        when(passwordEncoder.encode("password")).thenReturn("encoded");
        when(userRepository.save(any())).thenReturn(saved);
        when(jwtTokenProvider.generateToken(any(), any())).thenReturn("jwt-token");

        AuthResponse result = authService.register(req);

        assertNotNull(result);
        verify(roleRepository).save(any());
    }

    @Test
    void login_shouldAuthenticateAndReturnTokenWithCookie() {
        LoginRequest req = new LoginRequest();
        req.setEmail("user@example.com");
        req.setPassword("pass");
        HttpServletResponse httpResponse = mock(HttpServletResponse.class);

        Role role = customerRole();
        User user = User.builder().userId(1L).name("User").email("user@example.com")
                .password("encoded").role(role).build();

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(refreshTokenService.createRefreshToken(1L)).thenReturn("raw-refresh-token");
        when(jwtTokenProvider.generateToken(any(), any())).thenReturn("access-token");

        AuthResponse result = authService.login(req, httpResponse);

        assertEquals("access-token", result.getAccessToken());
        assertEquals("CUSTOMER", result.getRole());
        verify(httpResponse).addCookie(any());
    }

    @Test
    void logout_shouldInvalidateTokenAndClearCookie() {
        HttpServletResponse httpResponse = mock(HttpServletResponse.class);

        authService.logout("raw-refresh-token", httpResponse);

        verify(refreshTokenService).invalidateByRawToken("raw-refresh-token");
        verify(httpResponse).addCookie(any());
    }
}

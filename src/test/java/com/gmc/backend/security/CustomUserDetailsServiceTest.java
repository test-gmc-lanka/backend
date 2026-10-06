package com.gmc.backend.security;

import com.gmc.backend.model.Role;
import com.gmc.backend.model.User;
import com.gmc.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService service;

    @Test
    void loadUserByUsername_shouldReturnUserDetailsWhenUserExists() {
        Role role = Role.builder().roleId(1L).roleName("CUSTOMER").build();
        User user = User.builder()
                .userId(1L)
                .name("Test User")
                .email("test@example.com")
                .password("encoded-pass")
                .role(role)
                .build();
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

        UserDetails result = service.loadUserByUsername("test@example.com");

        assertEquals("test@example.com", result.getUsername());
    }

    @Test
    void loadUserByUsername_shouldThrowUsernameNotFoundExceptionWhenMissing() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
                () -> service.loadUserByUsername("missing@example.com"));
    }
}

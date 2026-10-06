package com.gmc.backend.service.impl;

import com.gmc.backend.dto.request.ChangePasswordRequest;
import com.gmc.backend.dto.request.CreateUserRequest;
import com.gmc.backend.dto.request.UpdateProfileRequest;
import com.gmc.backend.dto.response.UserResponse;
import com.gmc.backend.exception.BusinessRuleException;
import com.gmc.backend.exception.DuplicateEmailException;
import com.gmc.backend.exception.ResourceNotFoundException;
import com.gmc.backend.model.Role;
import com.gmc.backend.model.User;
import com.gmc.backend.repository.RoleRepository;
import com.gmc.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl userService;

    private Role role() {
        return Role.builder().roleId(1L).roleName("CUSTOMER").build();
    }

    private User user() {
        return User.builder().userId(1L).name("Alice").email("alice@example.com")
                .password("encoded-pass").phone("0771234567").role(role()).active(true).build();
    }

    @Test
    void getProfile_shouldReturnUserResponseWhenFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user()));

        UserResponse result = userService.getProfile(1L);

        assertEquals("Alice", result.getName());
        assertEquals("alice@example.com", result.getEmail());
    }

    @Test
    void getProfile_shouldThrowWhenUserNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.getProfile(99L));
    }

    @Test
    void updateProfile_shouldUpdateNameWhenProvided() {
        User u = user();
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));
        when(userRepository.save(any())).thenReturn(u);

        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setName("Updated Alice");
        req.setPhone("0779876543");

        userService.updateProfile(1L, req);

        assertEquals("Updated Alice", u.getName());
        verify(userRepository).save(u);
    }

    @Test
    void changePassword_shouldThrowWhenCurrentPasswordIsIncorrect() {
        User u = user();
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("wrong-pass", "encoded-pass")).thenReturn(false);

        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setCurrentPassword("wrong-pass");
        req.setNewPassword("new-pass");

        assertThrows(BusinessRuleException.class, () -> userService.changePassword(1L, req));
    }

    @Test
    void changePassword_shouldUpdatePasswordWhenCurrentPasswordCorrect() {
        User u = user();
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("correct-pass", "encoded-pass")).thenReturn(true);
        when(passwordEncoder.encode("new-pass")).thenReturn("new-encoded");

        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setCurrentPassword("correct-pass");
        req.setNewPassword("new-pass");

        userService.changePassword(1L, req);

        assertEquals("new-encoded", u.getPassword());
        verify(userRepository).save(u);
    }

    @Test
    void getAllUsers_shouldReturnAllUsers() {
        when(userRepository.findAll()).thenReturn(List.of(user()));

        List<UserResponse> result = userService.getAllUsers();

        assertEquals(1, result.size());
    }

    @Test
    void getUsersByRole_shouldReturnOnlyMatchingRole() {
        Role adminRole = Role.builder().roleId(2L).roleName("ADMIN").build();
        User admin = User.builder().userId(2L).name("Admin").email("admin@test.com")
                .password("pass").role(adminRole).active(true).build();
        when(userRepository.findAll()).thenReturn(List.of(user(), admin));

        List<UserResponse> result = userService.getUsersByRole("CUSTOMER");

        assertEquals(1, result.size());
        assertEquals("Alice", result.get(0).getName());
    }

    @Test
    void createUser_shouldThrowOnDuplicateEmail() {
        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

        CreateUserRequest req = new CreateUserRequest();
        req.setEmail("existing@example.com");
        req.setName("Test");
        req.setPassword("pass");
        req.setRole("CUSTOMER");

        assertThrows(DuplicateEmailException.class, () -> userService.createUser(req));
    }

    @Test
    void createUser_shouldSaveUserWithCorrectRole() {
        Role role = role();
        User saved = user();
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(roleRepository.findByRoleName("CUSTOMER")).thenReturn(Optional.of(role));
        when(passwordEncoder.encode("pass")).thenReturn("encoded");
        when(userRepository.save(any())).thenReturn(saved);

        CreateUserRequest req = new CreateUserRequest();
        req.setEmail("new@example.com");
        req.setName("Alice");
        req.setPassword("pass");
        req.setRole("CUSTOMER");

        UserResponse result = userService.createUser(req);

        assertEquals("CUSTOMER", result.getRole());
    }

    @Test
    void deactivateUser_shouldSetActiveToFalse() {
        User u = user();
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));
        when(userRepository.save(any())).thenReturn(u);

        userService.deactivateUser(1L);

        assertTrue(!u.getActive());
        verify(userRepository).save(u);
    }

    @Test
    void activateUser_shouldSetActiveToTrue() {
        User u = user();
        u.setActive(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));
        when(userRepository.save(any())).thenReturn(u);

        userService.activateUser(1L);

        assertTrue(u.getActive());
        verify(userRepository).save(u);
    }
}

package com.gmc.backend.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gmc.backend.dto.request.ChangePasswordRequest;
import com.gmc.backend.dto.request.CreateUserRequest;
import com.gmc.backend.dto.request.UpdateProfileRequest;
import com.gmc.backend.dto.response.UserResponse;
import com.gmc.backend.exception.BusinessRuleException;
import com.gmc.backend.exception.DuplicateEmailException;
import com.gmc.backend.exception.InvalidFileException;
import com.gmc.backend.exception.ResourceNotFoundException;
import com.gmc.backend.model.Role;
import com.gmc.backend.model.User;
import com.gmc.backend.repository.RefreshTokenRepository;
import com.gmc.backend.repository.RoleRepository;
import com.gmc.backend.repository.UserRepository;
import com.gmc.backend.service.FileStorageService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private UserServiceImpl userService;

    private Role role() {
        return Role.builder().roleId(1L).roleName("CUSTOMER").build();
    }

    private User user() {
        return User.builder()
                .userId(1L)
                .name("Alice")
                .email("alice@example.com")
                .password("encoded-pass")
                .phone("0771234567")
                .role(role())
                .active(true)
                .build();
    }

    @Test
    void getProfile_shouldReturnUserResponseWhenFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user()));

        UserResponse result = userService.getProfile(1L);

        assertEquals("Alice", result.getName());
        assertEquals("alice@example.com", result.getEmail());
    }

    @Test
    void updateProfileImage_shouldStoreInAvatarsFolderAndSaveUrl() {
        MockMultipartFile file = new MockMultipartFile("file", "me.png", "image/png", new byte[] {1});
        when(userRepository.findById(1L)).thenReturn(Optional.of(user()));
        when(fileStorageService.storeImages(List.of(file), "avatars")).thenReturn(List.of("/uploads/avatars/me.png"));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse result = userService.updateProfileImage(1L, file);

        assertEquals("/uploads/avatars/me.png", result.getProfileImageUrl());
    }

    @Test
    void updateProfileImage_shouldThrowWhenNothingStored() {
        MockMultipartFile empty = new MockMultipartFile("file", "me.png", "image/png", new byte[0]);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user()));
        when(fileStorageService.storeImages(List.of(empty), "avatars")).thenReturn(List.of());

        assertThrows(InvalidFileException.class, () -> userService.updateProfileImage(1L, empty));
    }

    @Test
    void removeProfileImage_shouldClearUrl() {
        User user = user();
        user.setProfileImageUrl("/uploads/avatars/old.png");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse result = userService.removeProfileImage(1L);

        assertNull(result.getProfileImageUrl());
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
        // Other sessions are signed out after a password change
        verify(refreshTokenRepository).deleteAllByUser(u);
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
        User admin = User.builder()
                .userId(2L)
                .name("Admin")
                .email("admin@test.com")
                .password("pass")
                .role(adminRole)
                .active(true)
                .build();
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

    @Test
    void deactivateUser_shouldRefuseTheLastActiveAdmin() {
        User admin = User.builder()
                .userId(5L)
                .name("Admin")
                .email("admin@x.com")
                .role(Role.builder().roleId(2L).roleName("ADMIN").build())
                .active(true)
                .build();
        when(userRepository.findById(5L)).thenReturn(Optional.of(admin));
        when(userRepository.findByRole_RoleName("ADMIN")).thenReturn(List.of(admin));

        assertThrows(BusinessRuleException.class, () -> userService.deactivateUser(5L));
        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_shouldRejectUnknownRoleInsteadOfCreatingIt() {
        CreateUserRequest req = new CreateUserRequest();
        req.setName("Bob");
        req.setEmail("Bob@Example.com ");
        req.setPassword("password123");
        req.setRole("ADMN");
        when(userRepository.existsByEmail("bob@example.com")).thenReturn(false);

        assertThrows(BusinessRuleException.class, () -> userService.createUser(req));
        verify(roleRepository, never()).save(any());
    }
}

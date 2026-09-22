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
import com.gmc.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserResponse getProfile(Long userId) {
        return toResponse(findById(userId));
    }

    @Override
    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = findById(userId);
        if (request.getName() != null && !request.getName().isBlank()) {
            user.setName(request.getName());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        if (request.getAddress() != null) {
            user.setAddress(request.getAddress());
        }
        return toResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = findById(userId);
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BusinessRuleException("Current password is incorrect");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    @Override
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<UserResponse> getUsersByRole(String role) {
        return userRepository.findAll().stream()
                .filter(u -> u.getRole().getRoleName().equalsIgnoreCase(role))
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateEmailException(request.getEmail());
        }
        Role role = roleRepository.findByRoleName(request.getRole().toUpperCase())
                .orElseGet(() -> roleRepository.save(
                        Role.builder().roleName(request.getRole().toUpperCase()).build()));
        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .address(request.getAddress())
                .role(role)
                .active(true)
                .build();
        return toResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public UserResponse deactivateUser(Long userId) {
        User user = findById(userId);
        user.setActive(false);
        return toResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public UserResponse activateUser(Long userId) {
        User user = findById(userId);
        user.setActive(true);
        return toResponse(userRepository.save(user));
    }

    private User findById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    public UserResponse toResponse(User user) {
        return UserResponse.builder()
                .userId(user.getUserId())
                .name(user.getName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .address(user.getAddress())
                .role(user.getRole().getRoleName())
                .active(user.getActive())
                .build();
    }
}

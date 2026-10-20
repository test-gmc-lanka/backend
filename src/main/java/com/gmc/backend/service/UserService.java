package com.gmc.backend.service;

import com.gmc.backend.dto.request.ChangePasswordRequest;
import com.gmc.backend.dto.request.CreateUserRequest;
import com.gmc.backend.dto.request.UpdateProfileRequest;
import com.gmc.backend.dto.response.UserResponse;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface UserService {

    UserResponse getProfile(Long userId);

    UserResponse updateProfile(Long userId, UpdateProfileRequest request);

    void changePassword(Long userId, ChangePasswordRequest request);

    UserResponse updateProfileImage(Long userId, MultipartFile file);

    UserResponse removeProfileImage(Long userId);

    List<UserResponse> getAllUsers();

    List<UserResponse> getUsersByRole(String role);

    UserResponse createUser(CreateUserRequest request);

    UserResponse deactivateUser(Long userId);

    UserResponse activateUser(Long userId);
}

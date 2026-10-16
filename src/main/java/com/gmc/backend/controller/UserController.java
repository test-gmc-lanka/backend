package com.gmc.backend.controller;

import jakarta.servlet.http.HttpServletResponse;
import com.gmc.backend.service.AuthService;
import com.gmc.backend.dto.request.ChangePasswordRequest;
import com.gmc.backend.dto.request.UpdateProfileRequest;
import com.gmc.backend.dto.response.NotificationResponse;
import com.gmc.backend.dto.response.UserResponse;
import com.gmc.backend.security.CustomUserDetails;
import com.gmc.backend.service.NotificationService;
import com.gmc.backend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final NotificationService notificationService;
    private final AuthService authService;
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getProfile(@AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(userService.getProfile(user.getId()));
    }

    @PutMapping("/me")
    public ResponseEntity<UserResponse> updateProfile(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateProfile(user.getId(), request));
    }

    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserResponse> uploadAvatar(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(userService.updateProfileImage(user.getId(), file));
    }

    @DeleteMapping("/me/avatar")
    public ResponseEntity<UserResponse> removeAvatar(@AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(userService.removeProfileImage(user.getId()));
    }

    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletResponse response) {
        userService.changePassword(user.getId(), request);
        authService.startNewSession(user.getId(), response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me/notifications")
    public ResponseEntity<List<NotificationResponse>> getNotifications(
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(notificationService.getNotifications(user.getId()));
    }

    @GetMapping("/me/notifications/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(Map.of("count", notificationService.countUnread(user.getId())));
    }

    @PostMapping("/me/notifications/{notificationId}/read")
    public ResponseEntity<Void> markRead(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long notificationId) {
        notificationService.markRead(user.getId(), notificationId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/me/notifications/mark-read")
    public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal CustomUserDetails user) {
        notificationService.markAllRead(user.getId());
        return ResponseEntity.noContent().build();
    }
}

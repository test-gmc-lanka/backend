package com.gmc.backend.service.impl;

import com.gmc.backend.dto.response.NotificationResponse;
import com.gmc.backend.exception.ResourceNotFoundException;
import com.gmc.backend.model.Notification;
import com.gmc.backend.model.User;
import com.gmc.backend.repository.NotificationRepository;
import com.gmc.backend.repository.UserRepository;
import com.gmc.backend.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @Override
    public List<NotificationResponse> getNotifications(Long userId) {
        return notificationRepository.findByUser_UserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public long countUnread(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        return notificationRepository.countByUserAndReadFalse(user);
    }

    @Override
    @Transactional
    public void markAllRead(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        notificationRepository.markAllReadByUser(user);
    }

    @Override
    @Transactional
    public void sendNotification(Long userId, String message, String type) {
        userRepository.findById(userId).ifPresent(user -> {
            Notification notification = Notification.builder()
                    .message(message)
                    .type(type)
                    .user(user)
                    .build();
            notificationRepository.save(notification);
        });
    }

    private NotificationResponse toResponse(Notification n) {
        return NotificationResponse.builder()
                .notificationId(n.getNotificationId())
                .message(n.getMessage())
                .type(n.getType())
                .read(n.getRead())
                .createdAt(n.getCreatedAt())
                .build();
    }
}

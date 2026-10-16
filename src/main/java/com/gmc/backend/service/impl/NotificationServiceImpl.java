package com.gmc.backend.service.impl;

import com.gmc.backend.dto.response.NotificationResponse;
import com.gmc.backend.exception.ResourceNotFoundException;
import com.gmc.backend.model.Notification;
import com.gmc.backend.model.User;
import com.gmc.backend.repository.NotificationRepository;
import com.gmc.backend.repository.UserRepository;
import com.gmc.backend.service.EmailService;
import com.gmc.backend.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    // Matches the notifications.message column; longer text would fail the whole transaction
    static final int MAX_MESSAGE_LENGTH = 500;

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl = "http://localhost:5173";

    @Override
    public List<NotificationResponse> getNotifications(Long userId) {
        return notificationRepository.findTop30ByUser_UserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public long countUnread(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        return notificationRepository.countByUserAndIsReadFalse(user);
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
    public void markRead(Long userId, Long notificationId) {
        notificationRepository.markReadByIdAndUser(notificationId, userId);
    }

    @Override
    @Transactional
    public void sendNotification(Long userId, String message, String type) {
        sendNotification(userId, message, type, null);
    }

    @Override
    @Transactional
    public void sendNotification(Long userId, String message, String type, String link) {
        userRepository.findById(userId).ifPresent(user -> save(user, message, type, link));
    }

    @Override
    @Transactional
    public void notifyAdmins(String message, String type, String link, String emailSubject) {
        for (User admin : userRepository.findByRole_RoleName("ADMIN")) {
            if (Boolean.FALSE.equals(admin.getActive())) {
                continue;
            }
            save(admin, message, type, link);
            if (emailSubject != null) {
                emailService.send(admin.getEmail(), "GMC Lanka Admin – " + emailSubject,
                        "Hi " + admin.getName() + ",\n\n" + message + "\n\n"
                                + (link == null ? "" : "Open: " + frontendUrl.replaceAll("/+$", "") + link + "\n\n")
                                + "— GMC Lanka system");
            }
        }
    }

    private void save(User user, String message, String type, String link) {
        notificationRepository.save(Notification.builder()
                .message(truncate(message))
                .type(type)
                .link(link)
                .user(user)
                .build());
    }

    static String truncate(String message) {
        if (message == null || message.length() <= MAX_MESSAGE_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_MESSAGE_LENGTH - 1) + "…";
    }

    private NotificationResponse toResponse(Notification n) {
        return NotificationResponse.builder()
                .notificationId(n.getNotificationId())
                .message(n.getMessage())
                .type(n.getType())
                .link(n.getLink())
                .read(n.getIsRead())
                .createdAt(n.getCreatedAt())
                .build();
    }
}

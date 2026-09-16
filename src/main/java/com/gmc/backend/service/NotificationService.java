package com.gmc.backend.service;

import com.gmc.backend.dto.response.NotificationResponse;

import java.util.List;

public interface NotificationService {

    List<NotificationResponse> getNotifications(Long userId);

    long countUnread(Long userId);

    void markAllRead(Long userId);

    void sendNotification(Long userId, String message, String type);
}

package com.gmc.backend.service;

import com.gmc.backend.dto.response.NotificationResponse;
import java.util.List;

/**
 * In-app notifications (the bell). Every event creates one; important events also send an
 * email, decided per event by the caller:
 *  - customers: order shipped/delivered/cancelled, payment receipts, appointment decisions,
 *    complaint resolved
 *  - admins: new appointment requests and complaints (someone is waiting on them)
 *  - technicians: new task assigned
 * Routine progress (order "processing", new orders for admins, ...) is in-app only.
 */
public interface NotificationService {

    List<NotificationResponse> getNotifications(Long userId);

    long countUnread(Long userId);

    void markAllRead(Long userId);

    void markRead(Long userId, Long notificationId);

    void sendNotification(Long userId, String message, String type);

    /** link is the frontend route opened when the notification is clicked, e.g. "/orders/12". */
    void sendNotification(Long userId, String message, String type, String link);

    /** Notifies every active admin; emailSubject != null also emails them. */
    void notifyAdmins(String message, String type, String link, String emailSubject);
}

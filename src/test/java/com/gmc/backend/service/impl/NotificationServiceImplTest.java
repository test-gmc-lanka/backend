package com.gmc.backend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gmc.backend.model.Notification;
import com.gmc.backend.model.User;
import com.gmc.backend.repository.NotificationRepository;
import com.gmc.backend.repository.UserRepository;
import com.gmc.backend.service.EmailService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    private User user(long id, String email, Boolean active) {
        return User.builder()
                .userId(id)
                .name("User " + id)
                .email(email)
                .active(active)
                .build();
    }

    @Test
    void sendNotification_savesMessageTypeAndLink() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1, "a@x.com", true)));

        notificationService.sendNotification(1L, "Order shipped", "ORDER", "/orders/5");

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(saved.capture());
        assertThat(saved.getValue().getMessage()).isEqualTo("Order shipped");
        assertThat(saved.getValue().getType()).isEqualTo("ORDER");
        assertThat(saved.getValue().getLink()).isEqualTo("/orders/5");
        assertThat(saved.getValue().getIsRead()).isFalse();
    }

    @Test
    void sendNotification_truncatesLongMessagesToFitTheColumn() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1, "a@x.com", true)));

        notificationService.sendNotification(1L, "x".repeat(1200), "APPOINTMENT", null);

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(saved.capture());
        assertThat(saved.getValue().getMessage())
                .hasSize(NotificationServiceImpl.MAX_MESSAGE_LENGTH)
                .endsWith("…");
    }

    @Test
    void sendNotification_unknownUserIsIgnored() {
        when(userRepository.findById(9L)).thenReturn(Optional.empty());

        notificationService.sendNotification(9L, "hi", "ORDER", null);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void notifyAdmins_inAppOnlyWhenNoEmailSubject() {
        when(userRepository.findByRole_RoleName("ADMIN"))
                .thenReturn(List.of(user(1, "a1@x.com", true), user(2, "a2@x.com", true)));

        notificationService.notifyAdmins("New order #5", "ORDER", "/admin/orders", null);

        verify(notificationRepository, times(2)).save(any(Notification.class));
        verify(emailService, never()).send(any(), any(), any());
    }

    @Test
    void notifyAdmins_emailsActiveAdminsAndSkipsDeactivatedOnes() {
        when(userRepository.findByRole_RoleName("ADMIN"))
                .thenReturn(List.of(user(1, "a1@x.com", true), user(2, "a2@x.com", false)));

        notificationService.notifyAdmins("New complaint", "COMPLAINT", "/admin/feedback", "New customer complaint");

        verify(notificationRepository, times(1)).save(any(Notification.class));
        verify(emailService).send(eq("a1@x.com"), contains("New customer complaint"), contains("/admin/feedback"));
        verify(emailService, never()).send(eq("a2@x.com"), anyString(), anyString());
    }

    @Test
    void markRead_isScopedToTheOwner() {
        notificationService.markRead(1L, 42L);

        verify(notificationRepository).markReadByIdAndUser(42L, 1L);
    }
}

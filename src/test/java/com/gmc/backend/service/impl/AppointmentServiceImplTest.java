package com.gmc.backend.service.impl;

import com.gmc.backend.dto.request.AppointmentRequest;
import com.gmc.backend.dto.request.RescheduleRequest;
import com.gmc.backend.dto.response.AppointmentResponse;
import com.gmc.backend.exception.BusinessRuleException;
import com.gmc.backend.exception.ResourceNotFoundException;
import com.gmc.backend.model.Appointment;
import com.gmc.backend.model.AppointmentStatus;
import com.gmc.backend.model.Role;
import com.gmc.backend.model.User;
import com.gmc.backend.repository.AppointmentRepository;
import com.gmc.backend.repository.UserRepository;
import com.gmc.backend.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceImplTest {

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private AppointmentServiceImpl appointmentService;

    // ── helpers ──────────────────────────────────────────────────────────────

    private User user() {
        Role role = Role.builder().roleId(1L).roleName("CUSTOMER").build();
        return User.builder().userId(1L).name("User").email("user@example.com")
                .password("pass").role(role).build();
    }

    private Appointment appointment(User u, AppointmentStatus status) {
        return Appointment.builder()
                .appointmentId(1L)
                .date(LocalDateTime.now().plusDays(1))
                .message("Test message")
                .status(status)
                .user(u)
                .build();
    }

    private RescheduleRequest rescheduleRequest(LocalDateTime newDate) {
        RescheduleRequest req = new RescheduleRequest();
        req.setNewDate(newDate);
        return req;
    }

    // Makes repository.save() return the same object it receives, so the
    // status/date set by the service can be checked on the response.
    private void saveReturnsArgument() {
        when(appointmentRepository.save(any(Appointment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void found(Appointment a) {
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(a));
    }

    // ════════════════════════ CUSTOMER: book ═════════════════════════════════

    @Test
    void bookAppointment_shouldSaveAndReturnPendingAppointment() {
        User u = user();
        Appointment saved = appointment(u, AppointmentStatus.PENDING);
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));
        when(appointmentRepository.save(any())).thenReturn(saved);

        AppointmentRequest req = new AppointmentRequest();
        req.setDate(LocalDateTime.now().plusDays(1));
        req.setMessage("Need service");

        AppointmentResponse result = appointmentService.bookAppointment(1L, req);

        assertEquals(AppointmentStatus.PENDING, result.getStatus());
        verify(notificationService).sendNotification(eq(1L), any(), eq("APPOINTMENT"));
    }

    @Test
    void bookAppointment_shouldThrowWhenUserNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        AppointmentRequest req = new AppointmentRequest();
        req.setDate(LocalDateTime.now().plusDays(1));
        req.setMessage("Test");

        assertThrows(ResourceNotFoundException.class,
                () -> appointmentService.bookAppointment(99L, req));
    }

    // ════════════════════════ CUSTOMER: reschedule ═══════════════════════════

    @Test
    void reschedule_shouldThrowWhenAppointmentIsCancelled() {
        found(appointment(user(), AppointmentStatus.CANCELLED));

        assertThrows(BusinessRuleException.class, () -> appointmentService.reschedule(
                1L, 1L, rescheduleRequest(LocalDateTime.now().plusDays(3))));
    }

    @Test
    void reschedule_shouldThrowWhenAppointmentIsCompleted() {
        found(appointment(user(), AppointmentStatus.COMPLETED));

        assertThrows(BusinessRuleException.class, () -> appointmentService.reschedule(
                1L, 1L, rescheduleRequest(LocalDateTime.now().plusDays(3))));
    }

    @Test
    void reschedule_shouldUpdateDateForPendingAppointment() {
        LocalDateTime newDate = LocalDateTime.now().plusDays(5);
        found(appointment(user(), AppointmentStatus.PENDING));
        saveReturnsArgument();

        AppointmentResponse result =
                appointmentService.reschedule(1L, 1L, rescheduleRequest(newDate));

        assertEquals(newDate, result.getDate());
        assertEquals(AppointmentStatus.PENDING, result.getStatus());
        verify(notificationService).sendNotification(eq(1L), any(), eq("APPOINTMENT"));
    }

    @Test
    void reschedule_shouldSetApprovedAppointmentBackToPending() {
        found(appointment(user(), AppointmentStatus.APPROVED));
        saveReturnsArgument();

        AppointmentResponse result = appointmentService.reschedule(
                1L, 1L, rescheduleRequest(LocalDateTime.now().plusDays(4)));

        assertEquals(AppointmentStatus.PENDING, result.getStatus());
    }

    @Test
    void reschedule_shouldThrowWhenAccessedByDifferentUser() {
        User owner = User.builder().userId(2L).name("Other").email("other@test.com")
                .password("pass").role(Role.builder().roleName("CUSTOMER").build()).build();
        found(appointment(owner, AppointmentStatus.PENDING));

        assertThrows(BusinessRuleException.class, () -> appointmentService.reschedule(
                1L, 1L, rescheduleRequest(LocalDateTime.now().plusDays(3))));
        verify(appointmentRepository, never()).save(any());
    }

    // ════════════════════════ CUSTOMER: cancel ═══════════════════════════════

    @Test
    void cancelAppointment_shouldThrowWhenAlreadyCompleted() {
        found(appointment(user(), AppointmentStatus.COMPLETED));

        assertThrows(BusinessRuleException.class,
                () -> appointmentService.cancelAppointment(1L, 1L));
    }

    @Test
    void cancelAppointment_shouldThrowWhenAlreadyCancelled() {
        found(appointment(user(), AppointmentStatus.CANCELLED));

        assertThrows(BusinessRuleException.class,
                () -> appointmentService.cancelAppointment(1L, 1L));
    }

    @Test
    void cancelAppointment_shouldSetStatusToCancelled() {
        User u = user();
        Appointment pending = appointment(u, AppointmentStatus.PENDING);
        Appointment cancelled = appointment(u, AppointmentStatus.CANCELLED);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(pending));
        when(appointmentRepository.save(any())).thenReturn(cancelled);

        AppointmentResponse result = appointmentService.cancelAppointment(1L, 1L);

        assertEquals(AppointmentStatus.CANCELLED, result.getStatus());
        verify(notificationService).sendNotification(eq(1L), any(), eq("APPOINTMENT"));
    }

    @Test
    void cancelAppointment_shouldAllowCancellingApprovedAppointment() {
        found(appointment(user(), AppointmentStatus.APPROVED));
        saveReturnsArgument();

        AppointmentResponse result = appointmentService.cancelAppointment(1L, 1L);

        assertEquals(AppointmentStatus.CANCELLED, result.getStatus());
    }

    @Test
    void cancelAppointment_shouldThrowWhenAccessedByDifferentUser() {
        User owner = User.builder().userId(2L).name("Other").email("other@test.com")
                .password("pass").role(Role.builder().roleName("CUSTOMER").build()).build();
        found(appointment(owner, AppointmentStatus.PENDING));

        assertThrows(BusinessRuleException.class,
                () -> appointmentService.cancelAppointment(1L, 1L));
    }

    // ════════════════════════ CUSTOMER / ADMIN: queries ══════════════════════

    @Test
    void getAppointmentsByUser_shouldReturnUserAppointments() {
        User u = user();
        when(appointmentRepository.findByUserUserId(1L))
                .thenReturn(List.of(appointment(u, AppointmentStatus.PENDING)));

        List<AppointmentResponse> result = appointmentService.getAppointmentsByUser(1L);

        assertEquals(1, result.size());
        assertEquals(AppointmentStatus.PENDING, result.get(0).getStatus());
    }

    @Test
    void getAllAppointments_shouldReturnEveryAppointment() {
        User u = user();
        when(appointmentRepository.findAll()).thenReturn(List.of(
                appointment(u, AppointmentStatus.PENDING),
                appointment(u, AppointmentStatus.APPROVED)));

        List<AppointmentResponse> result = appointmentService.getAllAppointments();

        assertEquals(2, result.size());
    }

    @Test
    void getAppointmentsByStatus_shouldReturnMatchingAppointments() {
        User u = user();
        when(appointmentRepository.findByStatus(AppointmentStatus.APPROVED))
                .thenReturn(List.of(appointment(u, AppointmentStatus.APPROVED)));

        List<AppointmentResponse> result =
                appointmentService.getAppointmentsByStatus(AppointmentStatus.APPROVED);

        assertEquals(1, result.size());
        assertEquals(AppointmentStatus.APPROVED, result.get(0).getStatus());
    }

    // ════════════════════════ ADMIN: approve ═════════════════════════════════

    @Test
    void approveAppointment_shouldSetStatusToApprovedAndNotifyCustomer() {
        found(appointment(user(), AppointmentStatus.PENDING));
        saveReturnsArgument();

        AppointmentResponse result = appointmentService.approveAppointment(1L);

        assertEquals(AppointmentStatus.APPROVED, result.getStatus());
        verify(notificationService).sendNotification(eq(1L), any(), eq("APPOINTMENT"));
    }

    @Test
    void approveAppointment_shouldAllowRescheduledAppointment() {
        found(appointment(user(), AppointmentStatus.RESCHEDULED));
        saveReturnsArgument();

        AppointmentResponse result = appointmentService.approveAppointment(1L);

        assertEquals(AppointmentStatus.APPROVED, result.getStatus());
    }

    @Test
    void approveAppointment_shouldThrowWhenCancelled() {
        found(appointment(user(), AppointmentStatus.CANCELLED));

        assertThrows(BusinessRuleException.class,
                () -> appointmentService.approveAppointment(1L));
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void approveAppointment_shouldThrowWhenAlreadyApproved() {
        found(appointment(user(), AppointmentStatus.APPROVED));

        assertThrows(BusinessRuleException.class,
                () -> appointmentService.approveAppointment(1L));
    }

    @Test
    void approveAppointment_shouldThrowWhenNotFound() {
        when(appointmentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> appointmentService.approveAppointment(99L));
    }

    // ════════════════════════ ADMIN: reschedule ══════════════════════════════

    @Test
    void adminReschedule_shouldSetNewDateAndRescheduledStatus() {
        LocalDateTime newDate = LocalDateTime.now().plusDays(7);
        found(appointment(user(), AppointmentStatus.PENDING));
        saveReturnsArgument();

        AppointmentResponse result =
                appointmentService.adminReschedule(1L, rescheduleRequest(newDate));

        assertEquals(newDate, result.getDate());
        assertEquals(AppointmentStatus.RESCHEDULED, result.getStatus());
        verify(notificationService).sendNotification(eq(1L), any(), eq("APPOINTMENT"));
    }

    @Test
    void adminReschedule_shouldWorkOnAnotherUsersAppointment() {
        User owner = User.builder().userId(2L).name("Other").email("other@test.com")
                .password("pass").role(Role.builder().roleName("CUSTOMER").build()).build();
        found(appointment(owner, AppointmentStatus.APPROVED));
        saveReturnsArgument();

        AppointmentResponse result = appointmentService.adminReschedule(
                1L, rescheduleRequest(LocalDateTime.now().plusDays(2)));

        assertEquals(AppointmentStatus.RESCHEDULED, result.getStatus());
        verify(notificationService).sendNotification(eq(2L), any(), eq("APPOINTMENT"));
    }

    @Test
    void adminReschedule_shouldThrowWhenCancelled() {
        found(appointment(user(), AppointmentStatus.CANCELLED));

        assertThrows(BusinessRuleException.class, () -> appointmentService.adminReschedule(
                1L, rescheduleRequest(LocalDateTime.now().plusDays(2))));
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void adminReschedule_shouldThrowWhenNotFound() {
        when(appointmentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> appointmentService.adminReschedule(
                99L, rescheduleRequest(LocalDateTime.now().plusDays(2))));
    }

    // ════════════════════════ ADMIN: cancel ══════════════════════════════════

    @Test
    void adminCancel_shouldSetStatusToCancelledAndNotifyCustomer() {
        found(appointment(user(), AppointmentStatus.APPROVED));
        saveReturnsArgument();

        AppointmentResponse result = appointmentService.adminCancel(1L);

        assertEquals(AppointmentStatus.CANCELLED, result.getStatus());
        verify(notificationService).sendNotification(eq(1L), any(), eq("APPOINTMENT"));
    }

    @Test
    void adminCancel_shouldThrowWhenAlreadyCancelled() {
        found(appointment(user(), AppointmentStatus.CANCELLED));

        assertThrows(BusinessRuleException.class, () -> appointmentService.adminCancel(1L));
    }

    @Test
    void adminCancel_shouldThrowWhenCompleted() {
        found(appointment(user(), AppointmentStatus.COMPLETED));

        assertThrows(BusinessRuleException.class, () -> appointmentService.adminCancel(1L));
    }

    // ════════════════════════ ADMIN: updateStatus ════════════════════════════

    @Test
    void updateStatus_approvedShouldApprovePendingAppointment() {
        found(appointment(user(), AppointmentStatus.PENDING));
        saveReturnsArgument();

        AppointmentResponse result =
                appointmentService.updateStatus(1L, AppointmentStatus.APPROVED);

        assertEquals(AppointmentStatus.APPROVED, result.getStatus());
    }

    @Test
    void updateStatus_cancelledShouldCancelAppointment() {
        found(appointment(user(), AppointmentStatus.PENDING));
        saveReturnsArgument();

        AppointmentResponse result =
                appointmentService.updateStatus(1L, AppointmentStatus.CANCELLED);

        assertEquals(AppointmentStatus.CANCELLED, result.getStatus());
    }

    @Test
    void updateStatus_completedShouldCompleteApprovedAppointment() {
        found(appointment(user(), AppointmentStatus.APPROVED));
        saveReturnsArgument();

        AppointmentResponse result =
                appointmentService.updateStatus(1L, AppointmentStatus.COMPLETED);

        assertEquals(AppointmentStatus.COMPLETED, result.getStatus());
        verify(notificationService).sendNotification(eq(1L), any(), eq("APPOINTMENT"));
    }

    @Test
    void updateStatus_completedShouldThrowWhenNotApproved() {
        found(appointment(user(), AppointmentStatus.PENDING));

        assertThrows(BusinessRuleException.class,
                () -> appointmentService.updateStatus(1L, AppointmentStatus.COMPLETED));
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void updateStatus_rescheduledShouldBeRejected() {
        assertThrows(BusinessRuleException.class,
                () -> appointmentService.updateStatus(1L, AppointmentStatus.RESCHEDULED));
    }

    @Test
    void updateStatus_pendingShouldBeRejected() {
        assertThrows(BusinessRuleException.class,
                () -> appointmentService.updateStatus(1L, AppointmentStatus.PENDING));
    }
}

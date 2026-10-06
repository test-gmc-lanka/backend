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

    @Test
    void reschedule_shouldThrowWhenAppointmentIsCancelled() {
        User u = user();
        Appointment cancelled = appointment(u, AppointmentStatus.CANCELLED);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(cancelled));

        RescheduleRequest req = new RescheduleRequest();
        req.setNewDate(LocalDateTime.now().plusDays(3));

        assertThrows(BusinessRuleException.class, () -> appointmentService.reschedule(1L, 1L, req));
    }

    @Test
    void reschedule_shouldThrowWhenAppointmentIsCompleted() {
        User u = user();
        Appointment completed = appointment(u, AppointmentStatus.COMPLETED);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(completed));

        RescheduleRequest req = new RescheduleRequest();
        req.setNewDate(LocalDateTime.now().plusDays(3));

        assertThrows(BusinessRuleException.class, () -> appointmentService.reschedule(1L, 1L, req));
    }

    @Test
    void reschedule_shouldUpdateDateForPendingAppointment() {
        User u = user();
        LocalDateTime newDate = LocalDateTime.now().plusDays(5);
        Appointment pending = appointment(u, AppointmentStatus.PENDING);
        Appointment rescheduled = Appointment.builder().appointmentId(1L)
                .date(newDate).message("Test message").status(AppointmentStatus.PENDING).user(u).build();
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(pending));
        when(appointmentRepository.save(any())).thenReturn(rescheduled);

        RescheduleRequest req = new RescheduleRequest();
        req.setNewDate(newDate);

        AppointmentResponse result = appointmentService.reschedule(1L, 1L, req);

        assertEquals(newDate, result.getDate());
        verify(notificationService).sendNotification(eq(1L), any(), eq("APPOINTMENT"));
    }

    @Test
    void cancelAppointment_shouldThrowWhenAlreadyCompleted() {
        User u = user();
        Appointment completed = appointment(u, AppointmentStatus.COMPLETED);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(completed));

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
    }

    @Test
    void cancelAppointment_shouldThrowWhenAccessedByDifferentUser() {
        User owner = User.builder().userId(2L).name("Other").email("other@test.com")
                .password("pass").role(Role.builder().roleName("CUSTOMER").build()).build();
        Appointment pending = appointment(owner, AppointmentStatus.PENDING);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(pending));

        assertThrows(BusinessRuleException.class,
                () -> appointmentService.cancelAppointment(1L, 1L));
    }

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
    void getAppointmentsByStatus_shouldReturnMatchingAppointments() {
        User u = user();
        when(appointmentRepository.findByStatus(AppointmentStatus.CONFIRMED))
                .thenReturn(List.of(appointment(u, AppointmentStatus.CONFIRMED)));

        List<AppointmentResponse> result =
                appointmentService.getAppointmentsByStatus(AppointmentStatus.CONFIRMED);

        assertEquals(1, result.size());
    }
}

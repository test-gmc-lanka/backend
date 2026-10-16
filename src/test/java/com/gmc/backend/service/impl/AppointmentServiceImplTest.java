package com.gmc.backend.service.impl;

import com.gmc.backend.dto.request.AppointmentRequest;
import com.gmc.backend.dto.request.RescheduleRequest;
import com.gmc.backend.dto.response.AppointmentResponse;
import com.gmc.backend.exception.BusinessRuleException;
import com.gmc.backend.exception.ResourceNotFoundException;
import com.gmc.backend.model.Appointment;
import com.gmc.backend.model.AppointmentStatus;
import com.gmc.backend.model.ProductCategory;
import com.gmc.backend.model.Role;
import com.gmc.backend.model.User;
import com.gmc.backend.repository.AppointmentRepository;
import com.gmc.backend.repository.ProductCategoryRepository;
import com.gmc.backend.repository.UserRepository;
import com.gmc.backend.service.EmailService;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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

    @Mock
    private ProductCategoryRepository categoryRepository;

    @Mock
    private EmailService emailService;

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
        verify(notificationService).sendNotification(eq(1L), any(), eq("APPOINTMENT"), any());
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
        verify(notificationService).sendNotification(eq(1L), any(), eq("APPOINTMENT"), any());
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

        assertThrows(ResourceNotFoundException.class, () -> appointmentService.reschedule(
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
        verify(notificationService).sendNotification(eq(1L), any(), eq("APPOINTMENT"), any());
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

        assertThrows(ResourceNotFoundException.class,
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

        AppointmentResponse result = appointmentService.approveAppointment(1L, null, null);

        assertEquals(AppointmentStatus.APPROVED, result.getStatus());
        verify(notificationService).sendNotification(eq(1L), any(), eq("APPOINTMENT"), any());
    }

    @Test
    void approveAppointment_shouldAllowRescheduledAppointment() {
        found(appointment(user(), AppointmentStatus.RESCHEDULED));
        saveReturnsArgument();

        AppointmentResponse result = appointmentService.approveAppointment(1L, null, null);

        assertEquals(AppointmentStatus.APPROVED, result.getStatus());
    }

    @Test
    void approveAppointment_shouldThrowWhenCancelled() {
        found(appointment(user(), AppointmentStatus.CANCELLED));

        assertThrows(BusinessRuleException.class,
                () -> appointmentService.approveAppointment(1L, null, null));
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void approveAppointment_shouldThrowWhenAlreadyApproved() {
        found(appointment(user(), AppointmentStatus.APPROVED));

        assertThrows(BusinessRuleException.class,
                () -> appointmentService.approveAppointment(1L, null, null));
    }

    @Test
    void approveAppointment_shouldThrowWhenNotFound() {
        when(appointmentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> appointmentService.approveAppointment(99L, null, null));
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
        verify(notificationService).sendNotification(eq(1L), any(), eq("APPOINTMENT"), any());
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
        verify(notificationService).sendNotification(eq(2L), any(), eq("APPOINTMENT"), any());
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

        AppointmentResponse result = appointmentService.adminCancel(1L, null);

        assertEquals(AppointmentStatus.CANCELLED, result.getStatus());
        verify(notificationService).sendNotification(eq(1L), any(), eq("APPOINTMENT"), any());
    }

    @Test
    void adminCancel_shouldThrowWhenAlreadyCancelled() {
        found(appointment(user(), AppointmentStatus.CANCELLED));

        assertThrows(BusinessRuleException.class, () -> appointmentService.adminCancel(1L, null));
    }

    @Test
    void adminCancel_shouldThrowWhenCompleted() {
        found(appointment(user(), AppointmentStatus.COMPLETED));

        assertThrows(BusinessRuleException.class, () -> appointmentService.adminCancel(1L, null));
    }

    // ════════════════════════ ADMIN: updateStatus ════════════════════════════

    @Test
    void updateStatus_approvedShouldApprovePendingAppointment() {
        found(appointment(user(), AppointmentStatus.PENDING));
        saveReturnsArgument();

        AppointmentResponse result =
                appointmentService.updateStatus(1L, AppointmentStatus.APPROVED, null, null);

        assertEquals(AppointmentStatus.APPROVED, result.getStatus());
    }

    @Test
    void updateStatus_cancelledShouldCancelAppointment() {
        found(appointment(user(), AppointmentStatus.PENDING));
        saveReturnsArgument();

        AppointmentResponse result =
                appointmentService.updateStatus(1L, AppointmentStatus.CANCELLED, null, null);

        assertEquals(AppointmentStatus.CANCELLED, result.getStatus());
    }

    @Test
    void updateStatus_completedShouldCompleteApprovedAppointment() {
        Appointment past = appointment(user(), AppointmentStatus.APPROVED);
        past.setDate(LocalDateTime.now().minusHours(2));
        found(past);
        saveReturnsArgument();

        AppointmentResponse result =
                appointmentService.updateStatus(1L, AppointmentStatus.COMPLETED, null, null);

        assertEquals(AppointmentStatus.COMPLETED, result.getStatus());
        verify(notificationService).sendNotification(eq(1L), any(), eq("APPOINTMENT"), any());
    }

    @Test
    void updateStatus_completedShouldThrowWhenNotApproved() {
        found(appointment(user(), AppointmentStatus.PENDING));

        assertThrows(BusinessRuleException.class,
                () -> appointmentService.updateStatus(1L, AppointmentStatus.COMPLETED, null, null));
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void updateStatus_rescheduledShouldBeRejected() {
        assertThrows(BusinessRuleException.class,
                () -> appointmentService.updateStatus(1L, AppointmentStatus.RESCHEDULED, null, null));
    }

    @Test
    void updateStatus_pendingShouldBeRejected() {
        assertThrows(BusinessRuleException.class,
                () -> appointmentService.updateStatus(1L, AppointmentStatus.PENDING, null, null));
    }

    // ════════════════════════ ADMIN: meeting link & notes ════════════════════

    @Test
    void approveAppointment_shouldStoreMeetingLinkAndNoteAndSendThem() {
        found(appointment(user(), AppointmentStatus.PENDING));
        saveReturnsArgument();

        AppointmentResponse result = appointmentService.approveAppointment(
                1L, "  https://meet.google.com/abc-defg-hij ", " Please join 5 minutes early ");

        assertEquals("https://meet.google.com/abc-defg-hij", result.getMeetingLink());
        assertEquals("Please join 5 minutes early", result.getAdminNote());
        verify(notificationService).sendNotification(
                eq(1L), contains("https://meet.google.com/abc-defg-hij"), eq("APPOINTMENT"), any());
        verify(emailService).send(eq("user@example.com"), contains("approved"),
                contains("Please join 5 minutes early"));
    }

    @Test
    void approveAppointment_withoutLink_shouldApproveForInPersonMeeting() {
        found(appointment(user(), AppointmentStatus.PENDING));
        saveReturnsArgument();

        AppointmentResponse result = appointmentService.approveAppointment(1L, "  ", null);

        assertEquals(AppointmentStatus.APPROVED, result.getStatus());
        assertNull(result.getMeetingLink());
    }

    @Test
    void approveAppointment_shouldRejectNonHttpLink() {
        found(appointment(user(), AppointmentStatus.PENDING));

        assertThrows(BusinessRuleException.class,
                () -> appointmentService.approveAppointment(1L, "javascript:alert(1)", null));
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void approveAppointment_shouldRejectMalformedLink() {
        found(appointment(user(), AppointmentStatus.PENDING));

        assertThrows(BusinessRuleException.class,
                () -> appointmentService.approveAppointment(1L, "meet google com", null));
    }

    @Test
    void adminCancel_shouldStoreNoteAndIncludeItInNotification() {
        found(appointment(user(), AppointmentStatus.APPROVED));
        saveReturnsArgument();

        AppointmentResponse result = appointmentService.adminCancel(1L, "Technician unavailable");

        assertEquals("Technician unavailable", result.getAdminNote());
        verify(notificationService).sendNotification(
                eq(1L), contains("Technician unavailable"), eq("APPOINTMENT"), any());
    }

    @Test
    void adminReschedule_shouldStoreNote() {
        found(appointment(user(), AppointmentStatus.PENDING));
        saveReturnsArgument();
        RescheduleRequest request = rescheduleRequest(LocalDateTime.now().plusDays(3));
        request.setNote("Moved to the afternoon");

        AppointmentResponse result = appointmentService.adminReschedule(1L, request);

        assertEquals("Moved to the afternoon", result.getAdminNote());
    }

    @Test
    void updateStatus_completedShouldStoreNote() {
        Appointment past = appointment(user(), AppointmentStatus.APPROVED);
        past.setDate(LocalDateTime.now().minusHours(2));
        found(past);
        saveReturnsArgument();

        AppointmentResponse result = appointmentService.updateStatus(
                1L, AppointmentStatus.COMPLETED, null, "Thanks for visiting");

        assertEquals("Thanks for visiting", result.getAdminNote());
    }

    // ════════════════════════ notifications to admins ════════════════════════

    @Test
    void bookAppointment_shouldNotifyAdminsInAppAndByEmail() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user()));
        saveReturnsArgument();
        AppointmentRequest request = new AppointmentRequest();
        request.setDate(LocalDateTime.now().plusDays(2));
        request.setMessage("Need a custom wardrobe");

        appointmentService.bookAppointment(1L, request);

        verify(notificationService).notifyAdmins(contains("Need a custom wardrobe"), eq("APPOINTMENT"),
                eq("/admin/appointments"), eq("New appointment request"));
    }

    @Test
    void customerCancel_shouldNotifyAdminsInAppOnly() {
        found(appointment(user(), AppointmentStatus.APPROVED));
        saveReturnsArgument();

        appointmentService.cancelAppointment(1L, 1L);

        verify(notificationService).notifyAdmins(contains("cancelled their appointment"), eq("APPOINTMENT"),
                eq("/admin/appointments"), isNull());
    }

    // ════════════════════════ customer images ═════════════════════════════════

    @Test
    void bookAppointment_shouldStoreUploadedImages() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user()));
        saveReturnsArgument();
        AppointmentRequest request = new AppointmentRequest();
        request.setDate(LocalDateTime.now().plusDays(2));
        request.setMessage("Custom wardrobe");
        request.setImageUrls(List.of("/uploads/appointments/3f2a-11.jpg", "/uploads/appointments/9b1c-22.png"));

        AppointmentResponse result = appointmentService.bookAppointment(1L, request);

        assertEquals(2, result.getImageUrls().size());
        verify(notificationService).notifyAdmins(contains("2 images attached"), eq("APPOINTMENT"),
                eq("/admin/appointments"), eq("New appointment request"));
    }

    @Test
    void bookAppointment_shouldRejectOutsideImageLinks() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user()));
        AppointmentRequest request = new AppointmentRequest();
        request.setDate(LocalDateTime.now().plusDays(2));
        request.setMessage("Custom wardrobe");
        request.setImageUrls(List.of("https://evil.example.com/x.jpg"));

        assertThrows(BusinessRuleException.class, () -> appointmentService.bookAppointment(1L, request));
        verify(appointmentRepository, never()).save(any());
    }

    // ════════════════════════ related product line ═══════════════════════════

    @Test
    void bookAppointment_shouldSaveProductLineAndMentionItToAdmins() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user()));
        when(categoryRepository.findById(3L))
                .thenReturn(Optional.of(ProductCategory.builder().categoryId(3L).name("Furniture").build()));
        saveReturnsArgument();
        AppointmentRequest request = new AppointmentRequest();
        request.setDate(LocalDateTime.now().plusDays(2));
        request.setMessage("Custom wardrobe");
        request.setCategoryId(3L);

        AppointmentResponse result = appointmentService.bookAppointment(1L, request);

        assertEquals(3L, result.getCategoryId());
        assertEquals("Furniture", result.getCategoryName());
        verify(notificationService).notifyAdmins(contains("about Furniture"), eq("APPOINTMENT"),
                eq("/admin/appointments"), eq("New appointment request"));
    }

    @Test
    void bookAppointment_shouldRejectUnknownProductLine() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user()));
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());
        AppointmentRequest request = new AppointmentRequest();
        request.setDate(LocalDateTime.now().plusDays(2));
        request.setMessage("Custom wardrobe");
        request.setCategoryId(99L);

        assertThrows(BusinessRuleException.class, () -> appointmentService.bookAppointment(1L, request));
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void completing_futureAppointment_isRejected() {
        found(appointment(user(), AppointmentStatus.APPROVED));

        assertThrows(BusinessRuleException.class,
                () -> appointmentService.updateStatus(1L, AppointmentStatus.COMPLETED, null, null));
    }

    @Test
    void approving_appointmentWhoseTimePassed_isRejected() {
        Appointment past = appointment(user(), AppointmentStatus.PENDING);
        past.setDate(LocalDateTime.now().minusDays(1));
        found(past);

        assertThrows(BusinessRuleException.class, () -> appointmentService.approveAppointment(1L, null, null));
    }
}

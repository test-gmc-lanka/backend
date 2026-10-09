package com.gmc.backend.service.impl;

import com.gmc.backend.dto.request.AppointmentRequest;
import com.gmc.backend.dto.request.RescheduleRequest;
import com.gmc.backend.dto.response.AppointmentResponse;
import com.gmc.backend.exception.BusinessRuleException;
import com.gmc.backend.exception.ResourceNotFoundException;
import com.gmc.backend.model.Appointment;
import com.gmc.backend.model.AppointmentStatus;
import com.gmc.backend.model.User;
import com.gmc.backend.repository.AppointmentRepository;
import com.gmc.backend.repository.UserRepository;
import com.gmc.backend.service.AppointmentService;
import com.gmc.backend.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

import static com.gmc.backend.model.AppointmentStatus.APPROVED;
import static com.gmc.backend.model.AppointmentStatus.CANCELLED;
import static com.gmc.backend.model.AppointmentStatus.COMPLETED;
import static com.gmc.backend.model.AppointmentStatus.PENDING;
import static com.gmc.backend.model.AppointmentStatus.RESCHEDULED;

@Service
@RequiredArgsConstructor
public class AppointmentServiceImpl implements AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    // ════════════════════════════ CUSTOMER SIDE ════════════════════════════

    @Override
    @Transactional
    public AppointmentResponse bookAppointment(Long userId, AppointmentRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        Appointment appointment = Appointment.builder()
                .date(request.getDate())
                .message(request.getMessage())
                .status(PENDING)
                .user(user)
                .build();
        Appointment saved = appointmentRepository.save(appointment);
        notifyUser(userId, "Appointment requested for " + request.getDate());
        return toResponse(saved);
    }

    // Customer asks for a new time -> goes back to PENDING so the admin must approve it again
    @Override
    @Transactional
    public AppointmentResponse reschedule(Long appointmentId, Long userId, RescheduleRequest request) {
        Appointment appointment = findByIdAndUser(appointmentId, userId);
        requireStatus(appointment, "reschedule", PENDING, APPROVED, RESCHEDULED);
        appointment.setDate(request.getNewDate());
        appointment.setStatus(PENDING);
        Appointment saved = appointmentRepository.save(appointment);
        notifyUser(userId, "Reschedule requested for " + request.getNewDate());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public AppointmentResponse cancelAppointment(Long appointmentId, Long userId) {
        Appointment appointment = findByIdAndUser(appointmentId, userId);
        requireStatus(appointment, "cancel", PENDING, APPROVED, RESCHEDULED);
        appointment.setStatus(CANCELLED);
        Appointment saved = appointmentRepository.save(appointment);
        notifyUser(userId, "Your appointment was cancelled");
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AppointmentResponse> getAppointmentsByUser(Long userId) {
        return appointmentRepository.findByUserUserId(userId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ════════════════════════════ ADMIN SIDE ═══════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public List<AppointmentResponse> getAllAppointments() {
        return appointmentRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AppointmentResponse> getAppointmentsByStatus(AppointmentStatus status) {
        return appointmentRepository.findByStatus(status).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // APPROVED button
    @Override
    @Transactional
    public AppointmentResponse approveAppointment(Long appointmentId) {
        Appointment appointment = findById(appointmentId);
        requireStatus(appointment, "approve", PENDING, RESCHEDULED);
        appointment.setStatus(APPROVED);
        Appointment saved = appointmentRepository.save(appointment);
        notifyUser(appointment, "Your appointment on " + appointment.getDate() + " was approved");
        return toResponse(saved);
    }

    // RESCHEDULED button (admin picks the new date/time)
    @Override
    @Transactional
    public AppointmentResponse adminReschedule(Long appointmentId, RescheduleRequest request) {
        Appointment appointment = findById(appointmentId);
        requireStatus(appointment, "reschedule", PENDING, APPROVED, RESCHEDULED);
        appointment.setDate(request.getNewDate());
        appointment.setStatus(RESCHEDULED);
        Appointment saved = appointmentRepository.save(appointment);
        notifyUser(appointment, "Your appointment was rescheduled to " + request.getNewDate());
        return toResponse(saved);
    }

    // CANCELLED button
    @Override
    @Transactional
    public AppointmentResponse adminCancel(Long appointmentId) {
        Appointment appointment = findById(appointmentId);
        requireStatus(appointment, "cancel", PENDING, APPROVED, RESCHEDULED);
        appointment.setStatus(CANCELLED);
        Appointment saved = appointmentRepository.save(appointment);
        notifyUser(appointment, "Your appointment on " + appointment.getDate() + " was cancelled");
        return toResponse(saved);
    }

    // Generic status change. PENDING and RESCHEDULED cannot be set here:
    // RESCHEDULED needs a new date (use adminReschedule), PENDING is only set by the customer.
    @Override
    @Transactional
    public AppointmentResponse updateStatus(Long appointmentId, AppointmentStatus status) {
        return switch (status) {
            case APPROVED -> approveAppointment(appointmentId);
            case CANCELLED -> adminCancel(appointmentId);
            case COMPLETED -> complete(appointmentId);
            case RESCHEDULED -> throw new BusinessRuleException(
                    "Use the reschedule endpoint to set a new date");
            case PENDING -> throw new BusinessRuleException(
                    "Status cannot be set back to PENDING");
        };
    }

    private AppointmentResponse complete(Long appointmentId) {
        Appointment appointment = findById(appointmentId);
        requireStatus(appointment, "complete", APPROVED);
        appointment.setStatus(COMPLETED);
        Appointment saved = appointmentRepository.save(appointment);
        notifyUser(appointment, "Your appointment on " + appointment.getDate() + " is completed");
        return toResponse(saved);
    }

    // ════════════════════════════ HELPERS ══════════════════════════════════

    private Appointment findById(Long appointmentId) {
        return appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Appointment not found: " + appointmentId));
    }

    private Appointment findByIdAndUser(Long appointmentId, Long userId) {
        Appointment appointment = findById(appointmentId);
        if (!appointment.getUser().getUserId().equals(userId)) {
            throw new BusinessRuleException("Access denied to appointment: " + appointmentId);
        }
        return appointment;
    }

    // Allows the action only when the current status is one of the allowed ones
    private void requireStatus(Appointment appointment, String action, AppointmentStatus... allowed) {
        for (AppointmentStatus s : allowed) {
            if (appointment.getStatus() == s) {
                return;
            }
        }
        throw new BusinessRuleException("Cannot " + action + " an appointment that is "
                + appointment.getStatus());
    }

    private void notifyUser(Appointment appointment, String message) {
        notifyUser(appointment.getUser().getUserId(), message);
    }

    private void notifyUser(Long userId, String message) {
        notificationService.sendNotification(userId, message, "APPOINTMENT");
    }

    private AppointmentResponse toResponse(Appointment a) {
        return AppointmentResponse.builder()
                .appointmentId(a.getAppointmentId())
                .date(a.getDate())
                .message(a.getMessage())
                .status(a.getStatus())
                .userId(a.getUser().getUserId())
                .userName(a.getUser().getName())
                .build();
    }
}

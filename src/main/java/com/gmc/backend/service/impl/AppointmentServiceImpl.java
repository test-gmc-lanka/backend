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

@Service
@RequiredArgsConstructor
public class AppointmentServiceImpl implements AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public AppointmentResponse bookAppointment(Long userId, AppointmentRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        Appointment appointment = Appointment.builder()
                .date(request.getDate())
                .message(request.getMessage())
                .status(AppointmentStatus.PENDING)
                .user(user)
                .build();
        Appointment saved = appointmentRepository.save(appointment);
        notificationService.sendNotification(userId,
                "Appointment booked for " + request.getDate(), "APPOINTMENT");
        return toResponse(saved);
    }

    @Override
    @Transactional
    public AppointmentResponse reschedule(Long appointmentId, Long userId, RescheduleRequest request) {
        Appointment appointment = findByIdAndUser(appointmentId, userId);
        if (appointment.getStatus() == AppointmentStatus.CANCELLED
                || appointment.getStatus() == AppointmentStatus.COMPLETED) {
            throw new BusinessRuleException("Cannot reschedule a " + appointment.getStatus()
                    + " appointment");
        }
        appointment.setDate(request.getNewDate());
        Appointment saved = appointmentRepository.save(appointment);
        notificationService.sendNotification(userId,
                "Appointment rescheduled to " + request.getNewDate(), "APPOINTMENT");
        return toResponse(saved);
    }

    @Override
    @Transactional
    public AppointmentResponse cancelAppointment(Long appointmentId, Long userId) {
        Appointment appointment = findByIdAndUser(appointmentId, userId);
        if (appointment.getStatus() == AppointmentStatus.COMPLETED) {
            throw new BusinessRuleException("Cannot cancel a completed appointment");
        }
        appointment.setStatus(AppointmentStatus.CANCELLED);
        return toResponse(appointmentRepository.save(appointment));
    }

    @Override
    public List<AppointmentResponse> getAppointmentsByUser(Long userId) {
        return appointmentRepository.findByUserUserId(userId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<AppointmentResponse> getAllAppointments() {
        return appointmentRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<AppointmentResponse> getAppointmentsByStatus(AppointmentStatus status) {
        return appointmentRepository.findByStatus(status).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public AppointmentResponse updateStatus(Long appointmentId, AppointmentStatus status) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Appointment not found: " + appointmentId));
        appointment.setStatus(status);
        Appointment saved = appointmentRepository.save(appointment);
        notificationService.sendNotification(appointment.getUser().getUserId(),
                "Your appointment status updated to " + status, "APPOINTMENT");
        return toResponse(saved);
    }

    private Appointment findByIdAndUser(Long appointmentId, Long userId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Appointment not found: " + appointmentId));
        if (!appointment.getUser().getUserId().equals(userId)) {
            throw new BusinessRuleException("Access denied to appointment: " + appointmentId);
        }
        return appointment;
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

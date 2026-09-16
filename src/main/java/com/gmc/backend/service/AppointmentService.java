package com.gmc.backend.service;

import com.gmc.backend.dto.request.AppointmentRequest;
import com.gmc.backend.dto.request.RescheduleRequest;
import com.gmc.backend.dto.response.AppointmentResponse;
import com.gmc.backend.model.AppointmentStatus;

import java.util.List;

public interface AppointmentService {

    AppointmentResponse bookAppointment(Long userId, AppointmentRequest request);

    AppointmentResponse reschedule(Long appointmentId, Long userId, RescheduleRequest request);

    AppointmentResponse cancelAppointment(Long appointmentId, Long userId);

    List<AppointmentResponse> getAppointmentsByUser(Long userId);

    List<AppointmentResponse> getAllAppointments();

    List<AppointmentResponse> getAppointmentsByStatus(AppointmentStatus status);

    AppointmentResponse updateStatus(Long appointmentId, AppointmentStatus status);
}

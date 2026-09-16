package com.gmc.backend.dto.response;

import com.gmc.backend.model.AppointmentStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AppointmentResponse {

    private Long appointmentId;
    private LocalDateTime date;
    private String message;
    private AppointmentStatus status;
    private Long userId;
    private String userName;
}

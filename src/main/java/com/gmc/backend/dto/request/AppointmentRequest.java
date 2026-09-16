package com.gmc.backend.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AppointmentRequest {

    @NotNull(message = "Date is required")
    @Future(message = "Appointment date must be in the future")
    private LocalDateTime date;

    @NotBlank(message = "Message is required")
    private String message;
}

package com.gmc.backend.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RescheduleRequest {

    @NotNull(message = "New date is required")
    @Future(message = "Appointment date must be in the future")
    private LocalDateTime newDate;
}

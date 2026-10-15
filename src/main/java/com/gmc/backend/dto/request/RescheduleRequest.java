package com.gmc.backend.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RescheduleRequest {

    @NotNull(message = "New date is required")
    @Future(message = "Appointment date must be in the future")
    private LocalDateTime newDate;

    // Admin's note to the customer; ignored when the customer reschedules
    @Size(max = 1000, message = "Note must be at most 1000 characters")
    private String note;
}

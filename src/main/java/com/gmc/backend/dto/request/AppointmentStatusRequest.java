package com.gmc.backend.dto.request;

import com.gmc.backend.model.AppointmentStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AppointmentStatusRequest {

    @NotNull(message = "Status is required")
    private AppointmentStatus status;
}

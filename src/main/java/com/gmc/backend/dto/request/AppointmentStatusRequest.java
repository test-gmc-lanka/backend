package com.gmc.backend.dto.request;

import com.gmc.backend.model.AppointmentStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AppointmentStatusRequest {

    @NotNull(message = "Status is required")
    private AppointmentStatus status;

    // Only used when approving
    @Size(max = 500, message = "Meeting link must be at most 500 characters")
    private String meetingLink;

    @Size(max = 1000, message = "Note must be at most 1000 characters")
    private String note;
}

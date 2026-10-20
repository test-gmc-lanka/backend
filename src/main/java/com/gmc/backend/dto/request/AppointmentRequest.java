package com.gmc.backend.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class AppointmentRequest {

    @NotNull(message = "Date is required")
    @Future(message = "Appointment date must be in the future")
    private LocalDateTime date;

    @NotBlank(message = "Message is required")
    @Size(max = 1000, message = "Message must be at most 1000 characters")
    private String message;

    // Optional product line from the "Related product" dropdown
    private Long categoryId;

    // URLs returned by POST /api/appointments/images
    @Size(max = 6, message = "You can attach up to 6 images")
    private List<String> imageUrls;
}

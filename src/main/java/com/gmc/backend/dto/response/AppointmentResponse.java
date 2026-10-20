package com.gmc.backend.dto.response;

import com.gmc.backend.model.AppointmentStatus;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AppointmentResponse {

    private Long appointmentId;
    private LocalDateTime date;
    private String message;
    private AppointmentStatus status;
    private Long userId;
    private String userName;
    private String meetingLink;
    private String adminNote;
    private List<String> imageUrls;
    private Long categoryId;
    private String categoryName;
}

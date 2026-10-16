package com.gmc.backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class NotificationResponse {

    private Long notificationId;
    private String message;
    private String type;
    private String link;
    private Boolean read;
    private LocalDateTime createdAt;
}

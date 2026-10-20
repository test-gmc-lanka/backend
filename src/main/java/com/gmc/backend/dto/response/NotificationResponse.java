package com.gmc.backend.dto.response;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

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

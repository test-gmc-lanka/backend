package com.gmc.backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class FeedbackResponse {

    private Long feedbackId;
    private Long orderId;
    private Long productId;
    private String productName;
    private String productImageUrl;
    private Integer rating;
    private String comment;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean hidden;
    private Long userId;
    private String userName;
    private String userEmail;
}

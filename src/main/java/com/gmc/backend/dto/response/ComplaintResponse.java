package com.gmc.backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ComplaintResponse {

    private Long complaintId;
    private String description;
    private LocalDateTime date;
    private String status;
    private Long userId;
    private String userName;
}

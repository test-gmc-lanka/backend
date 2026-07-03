package com.gmc.backend.dto.response;

import com.gmc.backend.model.CustomDesignStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CustomDesignResponse {

    private Long designId;
    private String designName;
    private String description;
    private String filePath;
    private CustomDesignStatus status;
    private LocalDateTime createdDate;
    private Long userId;
    private String userName;
}

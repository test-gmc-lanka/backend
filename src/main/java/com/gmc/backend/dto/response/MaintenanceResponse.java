package com.gmc.backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class MaintenanceResponse {

    private Long maintenanceId;
    private String description;
    private String status;
    private BigDecimal cost;
    private Long productId;
    private String productName;
    private String title;
    private List<String> imageUrls;
    private Long technicianId;
    private String technicianName;
}

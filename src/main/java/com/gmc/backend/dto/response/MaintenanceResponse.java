package com.gmc.backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class MaintenanceResponse {

    private Long maintenanceId;
    private String description;
    private String status;
    private BigDecimal cost;
    private Long productId;
    private String productName;
    private Long technicianId;
    private String technicianName;
}

package com.gmc.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;
import lombok.Data;

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
    private LocalDate deadline;
}

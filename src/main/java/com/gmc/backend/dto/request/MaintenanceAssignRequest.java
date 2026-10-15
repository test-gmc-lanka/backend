package com.gmc.backend.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class MaintenanceAssignRequest {

    @NotBlank(message = "Description is required")
    private String description;

    // Either productId (catalog product) or title (custom design) must be given
    private Long productId;

    private String title;

    private List<String> imageUrls;

    @NotNull(message = "Technician ID is required")
    private Long technicianId;

    @NotNull(message = "Cost is required")
    @DecimalMin(value = "0.0", message = "Cost must be at least 0")
    private BigDecimal cost;
}

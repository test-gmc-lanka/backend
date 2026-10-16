package com.gmc.backend.dto.request;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class MaintenanceAssignRequest {

    @NotBlank(message = "Description is required")
    @Size(max = 2000, message = "Description must be at most 2000 characters")
    private String description;

    // Either productId (catalog product) or title (custom design) must be given
    private Long productId;

    @Size(max = 255, message = "Design name must be at most 255 characters")
    private String title;

    private List<String> imageUrls;

    @NotNull(message = "Technician ID is required")
    private Long technicianId;

    @NotNull(message = "Deadline is required")
    @FutureOrPresent(message = "Deadline can't be in the past")
    private LocalDate deadline;

    @NotNull(message = "Cost is required")
    @DecimalMin(value = "0.0", message = "Cost must be at least 0")
    private BigDecimal cost;
}

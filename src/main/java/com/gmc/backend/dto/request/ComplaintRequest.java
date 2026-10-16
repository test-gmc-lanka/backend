package com.gmc.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ComplaintRequest {

    @NotBlank(message = "Description is required")
    @Size(max = 2000, message = "Description must be at most 2000 characters")
    private String description;

    // Optional: the order (and product in it) the complaint is about
    private Long orderId;

    private Long productId;
}

package com.gmc.backend.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ProductRequest {

    @NotBlank(message = "Product name is required")
    private String name;

    private String description;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than 0")
    private BigDecimal price;

    @NotNull(message = "Category ID is required")
    private Long categoryId;

    private String material;

    // Cover image; must be one of imageUrls (defaults to the first image)
    private String imageUrl;

    private List<String> imageUrls;

    @Min(value = 0, message = "Stock quantity must be at least 0")
    private Integer stockQuantity;
}

package com.gmc.backend.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

@Data
public class ProductRequest {

    @NotBlank(message = "Product name is required")
    @Size(max = 255, message = "Name must be at most 255 characters")
    private String name;

    @Size(max = 2000, message = "Description must be at most 2000 characters")
    private String description;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than 0")
    private BigDecimal price;

    @NotNull(message = "Category ID is required")
    private Long categoryId;

    @Size(max = 255, message = "Material must be at most 255 characters")
    private String material;

    // Cover image; must be one of imageUrls (defaults to the first image)
    private String imageUrl;

    private List<String> imageUrls;

    @Min(value = 0, message = "Stock quantity must be at least 0")
    private Integer stockQuantity;
}

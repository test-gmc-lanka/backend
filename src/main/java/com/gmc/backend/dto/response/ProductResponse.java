package com.gmc.backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class ProductResponse {

    private Long productId;
    private String name;
    private String description;
    private BigDecimal price;
    private CategoryResponse category;
    private String material;
    private String imageUrl;
    private List<String> imageUrls;
    private Integer stockQuantity;
}

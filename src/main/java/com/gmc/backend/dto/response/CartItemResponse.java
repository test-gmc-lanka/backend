package com.gmc.backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class CartItemResponse {

    private Long cartItemId;
    private ProductResponse product;
    private Integer quantity;
    private BigDecimal subtotal;
}

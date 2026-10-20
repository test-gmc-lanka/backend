package com.gmc.backend.dto.response;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CartItemResponse {

    private Long cartItemId;
    private ProductResponse product;
    private Integer quantity;
    private BigDecimal subtotal;
}

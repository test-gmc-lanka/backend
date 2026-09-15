package com.gmc.backend.dto.response;

import com.gmc.backend.model.OrderStatus;
import com.gmc.backend.model.OrderType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class OrderResponse {

    private Long orderId;
    private LocalDateTime orderDate;
    private OrderStatus status;
    private OrderType orderType;
    private BigDecimal totalAmount;
    private String notes;
    private Long userId;
    private String userName;
    private List<OrderItemResponse> items;
}

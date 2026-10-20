package com.gmc.backend.dto.response;

import com.gmc.backend.model.OrderStatus;
import com.gmc.backend.model.OrderType;
import com.gmc.backend.model.PaymentMethod;
import com.gmc.backend.model.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OrderResponse {

    private Long orderId;
    private LocalDateTime orderDate;
    private OrderStatus status;
    private OrderType orderType;
    private BigDecimal totalAmount;
    private BigDecimal subtotal;
    private BigDecimal shippingFee;
    private BigDecimal taxAmount;
    private String notes;
    private Long userId;
    private String userName;
    private List<OrderItemResponse> items;

    private String shippingName;
    private String shippingPhone;
    private String shippingAddress;
    private String shippingCity;
    private String shippingPostalCode;

    private PaymentMethod paymentMethod;
    private PaymentStatus paymentStatus;
    private LocalDateTime paidAt;
}

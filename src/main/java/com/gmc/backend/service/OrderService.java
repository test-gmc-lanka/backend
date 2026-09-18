package com.gmc.backend.service;

import com.gmc.backend.dto.request.PlaceOrderRequest;
import com.gmc.backend.dto.response.OrderResponse;
import com.gmc.backend.model.OrderStatus;

import java.util.List;

public interface OrderService {

    OrderResponse placeOrder(Long userId, PlaceOrderRequest request);

    OrderResponse getOrderById(Long orderId, Long userId);

    List<OrderResponse> getOrdersByUser(Long userId);

    List<OrderResponse> getAllOrders();

    List<OrderResponse> getOrdersByStatus(OrderStatus status);

    OrderResponse updateOrderStatus(Long orderId, OrderStatus newStatus);
}

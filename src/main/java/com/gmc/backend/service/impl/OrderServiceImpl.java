package com.gmc.backend.service.impl;

import com.gmc.backend.dto.request.PlaceOrderRequest;
import com.gmc.backend.dto.response.OrderItemResponse;
import com.gmc.backend.dto.response.OrderResponse;
import com.gmc.backend.exception.BusinessRuleException;
import com.gmc.backend.exception.InvalidOrderStatusTransitionException;
import com.gmc.backend.exception.ResourceNotFoundException;
import com.gmc.backend.model.Cart;
import com.gmc.backend.model.CartItem;
import com.gmc.backend.model.Order;
import com.gmc.backend.model.OrderItem;
import com.gmc.backend.model.OrderStatus;
import com.gmc.backend.model.OrderType;
import com.gmc.backend.model.User;
import com.gmc.backend.repository.CartItemRepository;
import com.gmc.backend.repository.CartRepository;
import com.gmc.backend.repository.OrderRepository;
import com.gmc.backend.repository.UserRepository;
import com.gmc.backend.service.NotificationService;
import com.gmc.backend.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final Set<OrderStatus> TERMINAL_STATUSES =
            EnumSet.of(OrderStatus.DELIVERED, OrderStatus.CANCELLED);

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductServiceImpl productServiceImpl;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public OrderResponse placeOrder(Long userId, PlaceOrderRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        Cart cart = cartRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new BusinessRuleException("Cart is empty"));
        List<CartItem> cartItems = cartItemRepository.findByCart(cart);
        if (cartItems.isEmpty()) {
            throw new BusinessRuleException("Cart is empty");
        }
        OrderType orderType = (request.getOrderType() != null) ? request.getOrderType() : OrderType.B2C;
        Order newOrder = Order.builder()
                .orderDate(LocalDateTime.now())
                .status(OrderStatus.PENDING)
                .orderType(orderType)
                .notes(request.getNotes())
                .user(user)
                .build();
        List<OrderItem> orderItems = cartItems.stream()
                .map(ci -> OrderItem.builder()
                        .order(newOrder)
                        .product(ci.getProduct())
                        .quantity(ci.getQuantity())
                        .unitPrice(ci.getProduct().getPrice())
                        .build())
                .collect(Collectors.toList());
        newOrder.setItems(orderItems);
        BigDecimal total = orderItems.stream()
                .map(oi -> oi.getUnitPrice().multiply(BigDecimal.valueOf(oi.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        newOrder.setTotalAmount(total);
        Order saved = orderRepository.save(newOrder);
        cartItemRepository.deleteByCart(cart);
        notificationService.sendNotification(userId,
                "Your order #" + saved.getOrderId() + " has been placed successfully.", "ORDER");
        return toResponse(saved);
    }

    @Override
    public OrderResponse getOrderById(Long orderId, Long userId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        if (!order.getUser().getUserId().equals(userId)) {
            throw new BusinessRuleException("Access denied to order: " + orderId);
        }
        return toResponse(order);
    }

    @Override
    public List<OrderResponse> getOrdersByUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        return orderRepository.findByUser(user).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<OrderResponse> getOrdersByStatus(OrderStatus status) {
        return orderRepository.findByStatus(status).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public OrderResponse updateOrderStatus(Long orderId, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        if (TERMINAL_STATUSES.contains(order.getStatus())) {
            throw new InvalidOrderStatusTransitionException(order.getStatus(), newStatus);
        }
        order.setStatus(newStatus);
        Order saved = orderRepository.save(order);
        notificationService.sendNotification(order.getUser().getUserId(),
                "Order #" + orderId + " status updated to " + newStatus, "ORDER");
        return toResponse(saved);
    }

    private OrderResponse toResponse(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(oi -> OrderItemResponse.builder()
                        .orderItemId(oi.getOrderItemId())
                        .product(productServiceImpl.toResponse(oi.getProduct()))
                        .quantity(oi.getQuantity())
                        .unitPrice(oi.getUnitPrice())
                        .subtotal(oi.getUnitPrice().multiply(BigDecimal.valueOf(oi.getQuantity())))
                        .build())
                .collect(Collectors.toList());
        return OrderResponse.builder()
                .orderId(order.getOrderId())
                .orderDate(order.getOrderDate())
                .status(order.getStatus())
                .orderType(order.getOrderType())
                .totalAmount(order.getTotalAmount())
                .notes(order.getNotes())
                .userId(order.getUser().getUserId())
                .userName(order.getUser().getName())
                .items(items)
                .build();
    }
}

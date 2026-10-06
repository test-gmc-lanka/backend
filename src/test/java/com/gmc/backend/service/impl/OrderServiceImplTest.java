package com.gmc.backend.service.impl;

import com.gmc.backend.dto.request.PlaceOrderRequest;
import com.gmc.backend.dto.response.OrderResponse;
import com.gmc.backend.exception.BusinessRuleException;
import com.gmc.backend.exception.InvalidOrderStatusTransitionException;
import com.gmc.backend.exception.ResourceNotFoundException;
import com.gmc.backend.model.Cart;
import com.gmc.backend.model.CartItem;
import com.gmc.backend.model.Order;
import com.gmc.backend.model.OrderStatus;
import com.gmc.backend.model.OrderType;
import com.gmc.backend.model.Product;
import com.gmc.backend.model.ProductCategory;
import com.gmc.backend.model.Role;
import com.gmc.backend.model.User;
import com.gmc.backend.repository.CartItemRepository;
import com.gmc.backend.repository.CartRepository;
import com.gmc.backend.repository.OrderRepository;
import com.gmc.backend.repository.UserRepository;
import com.gmc.backend.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductServiceImpl productServiceImpl;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private OrderServiceImpl orderService;

    private User user() {
        Role role = Role.builder().roleId(1L).roleName("CUSTOMER").build();
        return User.builder().userId(1L).name("Alice").email("alice@example.com")
                .password("pass").role(role).build();
    }

    private Product product() {
        ProductCategory cat = ProductCategory.builder().categoryId(1L).name("Cat").build();
        return Product.builder().productId(1L).name("Product A")
                .price(new BigDecimal("100.00")).category(cat).stockQuantity(10).build();
    }

    private Order pendingOrder(User u) {
        return Order.builder().orderId(1L).status(OrderStatus.PENDING).orderType(OrderType.B2C)
                .user(u).orderDate(LocalDateTime.now()).totalAmount(BigDecimal.ZERO)
                .items(new ArrayList<>()).build();
    }

    @Test
    void placeOrder_shouldThrowWhenCartIsEmpty() {
        User u = user();
        Cart cart = new Cart();
        cart.setCartId(1L);
        cart.setUser(u);
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));
        when(cartRepository.findByUser_UserId(1L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCart(cart)).thenReturn(List.of());

        assertThrows(BusinessRuleException.class,
                () -> orderService.placeOrder(1L, new PlaceOrderRequest()));
    }

    @Test
    void placeOrder_shouldThrowWhenCartDoesNotExist() {
        User u = user();
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));
        when(cartRepository.findByUser_UserId(1L)).thenReturn(Optional.empty());

        assertThrows(BusinessRuleException.class,
                () -> orderService.placeOrder(1L, new PlaceOrderRequest()));
    }

    @Test
    void placeOrder_shouldSucceedAndClearCart() {
        User u = user();
        Cart cart = new Cart();
        cart.setCartId(1L);
        cart.setUser(u);
        CartItem ci = new CartItem();
        ci.setCart(cart);
        ci.setProduct(product());
        ci.setQuantity(2);

        Order saved = Order.builder().orderId(10L).orderDate(LocalDateTime.now())
                .status(OrderStatus.PENDING).orderType(OrderType.B2C)
                .totalAmount(new BigDecimal("200.00")).user(u).items(new ArrayList<>()).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(u));
        when(cartRepository.findByUser_UserId(1L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCart(cart)).thenReturn(List.of(ci));
        when(orderRepository.save(any())).thenReturn(saved);

        OrderResponse result = orderService.placeOrder(1L, new PlaceOrderRequest());

        assertEquals(10L, result.getOrderId());
        verify(cartItemRepository).deleteByCart(cart);
        verify(notificationService).sendNotification(eq(1L), any(), eq("ORDER"));
    }

    @Test
    void getOrderById_shouldThrowWhenOrderBelongsToDifferentUser() {
        User owner = User.builder().userId(2L).name("Bob").email("bob@example.com")
                .password("pass").role(Role.builder().roleName("CUSTOMER").build()).build();
        Order order = Order.builder().orderId(5L).user(owner).orderDate(LocalDateTime.now())
                .status(OrderStatus.PENDING).items(new ArrayList<>()).build();
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));

        assertThrows(BusinessRuleException.class, () -> orderService.getOrderById(5L, 1L));
    }

    @Test
    void getOrderById_shouldThrowWhenOrderNotFound() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> orderService.getOrderById(99L, 1L));
    }

    @Test
    void updateOrderStatus_shouldThrowForDeliveredOrder() {
        User u = user();
        Order delivered = Order.builder().orderId(1L).status(OrderStatus.DELIVERED)
                .user(u).orderDate(LocalDateTime.now()).items(new ArrayList<>()).build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(delivered));

        assertThrows(InvalidOrderStatusTransitionException.class,
                () -> orderService.updateOrderStatus(1L, OrderStatus.PROCESSING));
    }

    @Test
    void updateOrderStatus_shouldThrowForCancelledOrder() {
        User u = user();
        Order cancelled = Order.builder().orderId(1L).status(OrderStatus.CANCELLED)
                .user(u).orderDate(LocalDateTime.now()).items(new ArrayList<>()).build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(cancelled));

        assertThrows(InvalidOrderStatusTransitionException.class,
                () -> orderService.updateOrderStatus(1L, OrderStatus.PROCESSING));
    }

    @Test
    void updateOrderStatus_shouldSucceedForNonTerminalOrder() {
        User u = user();
        Order order = pendingOrder(u);
        Order updated = Order.builder().orderId(1L).status(OrderStatus.PROCESSING)
                .user(u).orderDate(LocalDateTime.now()).items(new ArrayList<>()).build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenReturn(updated);

        OrderResponse result = orderService.updateOrderStatus(1L, OrderStatus.PROCESSING);

        assertEquals(OrderStatus.PROCESSING, result.getStatus());
        verify(notificationService).sendNotification(eq(1L), any(), eq("ORDER"));
    }

    @Test
    void getOrdersByUser_shouldReturnUserOrders() {
        User u = user();
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));
        when(orderRepository.findByUser(u)).thenReturn(List.of(pendingOrder(u)));

        List<OrderResponse> result = orderService.getOrdersByUser(1L);

        assertEquals(1, result.size());
    }

    @Test
    void getOrdersByUser_shouldThrowWhenUserNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> orderService.getOrdersByUser(99L));
    }

    @Test
    void getAllOrders_shouldReturnAllOrders() {
        User u = user();
        when(orderRepository.findAll()).thenReturn(List.of(pendingOrder(u)));

        List<OrderResponse> result = orderService.getAllOrders();

        assertEquals(1, result.size());
    }
}

package com.gmc.backend.service.impl;

import com.gmc.backend.dto.request.PlaceOrderRequest;
import com.gmc.backend.dto.response.CheckoutSummaryResponse;
import com.gmc.backend.dto.response.OrderResponse;
import com.gmc.backend.dto.response.PaymentSessionResponse;
import com.gmc.backend.dto.response.PlaceOrderResponse;
import com.gmc.backend.exception.BusinessRuleException;
import com.gmc.backend.exception.InvalidOrderStatusTransitionException;
import com.gmc.backend.exception.ResourceNotFoundException;
import com.gmc.backend.model.Cart;
import com.gmc.backend.model.CartItem;
import com.gmc.backend.model.Order;
import com.gmc.backend.model.OrderItem;
import com.gmc.backend.model.OrderStatus;
import com.gmc.backend.model.OrderType;
import com.gmc.backend.model.PaymentMethod;
import com.gmc.backend.model.PaymentStatus;
import com.gmc.backend.model.Product;
import com.gmc.backend.model.ProductCategory;
import com.gmc.backend.model.Role;
import com.gmc.backend.model.User;
import com.gmc.backend.repository.CartItemRepository;
import com.gmc.backend.repository.CartRepository;
import com.gmc.backend.repository.OrderRepository;
import com.gmc.backend.repository.ProductRepository;
import com.gmc.backend.repository.UserRepository;
import com.gmc.backend.service.CheckoutPricing;
import com.gmc.backend.service.EmailService;
import com.gmc.backend.service.NotificationService;
import com.gmc.backend.service.StripeGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
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
    private ProductRepository productRepository;
    @Mock
    private ProductServiceImpl productServiceImpl;
    @Mock
    private NotificationService notificationService;
    @Mock
    private EmailService emailService;
    @Mock
    private StripeGateway stripeGateway;

    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        // Real pricing: 15% VAT, LKR 1,200 flat shipping, no free-shipping threshold
        CheckoutPricing pricing = new CheckoutPricing(
                new BigDecimal("0.15"), new BigDecimal("1200"), BigDecimal.ZERO);
        orderService = new OrderServiceImpl(orderRepository, userRepository, cartRepository,
                cartItemRepository, productRepository, productServiceImpl, notificationService,
                emailService, pricing, stripeGateway);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private User user() {
        Role role = Role.builder().roleId(1L).roleName("CUSTOMER").build();
        return User.builder().userId(1L).name("Alice").email("alice@example.com")
                .password("pass").role(role).build();
    }

    private Product product(int stock) {
        ProductCategory cat = ProductCategory.builder().categoryId(1L).name("Cat").build();
        return Product.builder().productId(1L).name("Product A")
                .price(new BigDecimal("100.00")).category(cat).stockQuantity(stock).build();
    }

    private Cart cartWith(User u, Product product, int quantity) {
        Cart cart = new Cart();
        cart.setCartId(1L);
        cart.setUser(u);
        CartItem ci = new CartItem();
        ci.setCart(cart);
        ci.setProduct(product);
        ci.setQuantity(quantity);
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));
        when(cartRepository.findByUser_UserId(1L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCart(cart)).thenReturn(List.of(ci));
        return cart;
    }

    private PlaceOrderRequest request(PaymentMethod method) {
        PlaceOrderRequest req = new PlaceOrderRequest();
        req.setShippingName(" Alice Perera ");
        req.setShippingPhone("0771234567");
        req.setShippingAddress("12 Main Street");
        req.setShippingCity("Colombo");
        req.setShippingPostalCode("00700");
        req.setPaymentMethod(method);
        return req;
    }

    // Saving assigns id 10 and returns the same object, like JPA does
    private void saveAssignsId() {
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            if (o.getOrderId() == null) {
                o.setOrderId(10L);
            }
            return o;
        });
    }

    private Order pendingOrder(User u) {
        return Order.builder().orderId(1L).status(OrderStatus.PENDING).orderType(OrderType.B2C)
                .user(u).orderDate(LocalDateTime.now()).totalAmount(BigDecimal.ZERO)
                .items(new ArrayList<>()).build();
    }

    // Unpaid card order for 2 × LKR 100 -> total LKR 1,430.00 (143000 cents)
    private Order unpaidCardOrder(User u, Product product) {
        Order order = Order.builder().orderId(10L).status(OrderStatus.PENDING).orderType(OrderType.B2C)
                .user(u).orderDate(LocalDateTime.now().minusHours(2))
                .subtotal(new BigDecimal("200.00")).shippingFee(new BigDecimal("1200.00"))
                .taxAmount(new BigDecimal("30.00")).totalAmount(new BigDecimal("1430.00"))
                .paymentMethod(PaymentMethod.CARD).paymentStatus(PaymentStatus.PENDING)
                .stripePaymentIntentId("pi_1").items(new ArrayList<>()).build();
        order.getItems().add(OrderItem.builder().order(order).product(product).quantity(2)
                .unitPrice(new BigDecimal("100.00")).build());
        return order;
    }

    private StripeGateway.Intent intent(String status, long amount) {
        return new StripeGateway.Intent("pi_1", status, amount, "lkr", "pi_1_secret");
    }

    // ════════════════════════ checkout summary ═══════════════════════════════

    @Test
    void getCheckoutSummary_shouldUseServerPricing() {
        Cart cart = new Cart();
        CartItem ci = new CartItem();
        ci.setProduct(product(10));
        ci.setQuantity(2);
        when(cartRepository.findByUser_UserId(1L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCart(cart)).thenReturn(List.of(ci));
        when(stripeGateway.isConfigured()).thenReturn(false);
        when(stripeGateway.currency()).thenReturn("lkr");

        CheckoutSummaryResponse summary = orderService.getCheckoutSummary(1L);

        assertEquals(new BigDecimal("200.00"), summary.getSubtotal());
        assertEquals(new BigDecimal("1200.00"), summary.getShippingFee());
        assertEquals(new BigDecimal("30.00"), summary.getTaxAmount());
        assertEquals(new BigDecimal("1430.00"), summary.getTotalAmount());
        assertEquals("LKR", summary.getCurrency());
        assertFalse(summary.isCardPaymentsEnabled());
        assertNull(summary.getStripePublishableKey());
    }

    // ════════════════════════ place order ════════════════════════════════════

    @Test
    void placeOrder_shouldThrowWhenCartIsEmpty() {
        User u = user();
        Cart cart = new Cart();
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));
        when(cartRepository.findByUser_UserId(1L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCart(cart)).thenReturn(List.of());

        assertThrows(BusinessRuleException.class,
                () -> orderService.placeOrder(1L, request(PaymentMethod.CASH_ON_DELIVERY)));
    }

    @Test
    void placeOrder_shouldThrowWhenCartDoesNotExist() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user()));
        when(cartRepository.findByUser_UserId(1L)).thenReturn(Optional.empty());

        assertThrows(BusinessRuleException.class,
                () -> orderService.placeOrder(1L, request(PaymentMethod.CASH_ON_DELIVERY)));
    }

    @Test
    void placeOrder_cashOnDelivery_shouldPriceReserveStockClearCartAndConfirm() {
        User u = user();
        Product product = product(10);
        Cart cart = cartWith(u, product, 2);
        saveAssignsId();

        PlaceOrderResponse result = orderService.placeOrder(1L, request(PaymentMethod.CASH_ON_DELIVERY));

        OrderResponse order = result.getOrder();
        assertEquals(10L, order.getOrderId());
        assertEquals(new BigDecimal("200.00"), order.getSubtotal());
        assertEquals(new BigDecimal("1200.00"), order.getShippingFee());
        assertEquals(new BigDecimal("30.00"), order.getTaxAmount());
        assertEquals(new BigDecimal("1430.00"), order.getTotalAmount());
        assertEquals("Alice Perera", order.getShippingName());
        assertEquals(PaymentMethod.CASH_ON_DELIVERY, order.getPaymentMethod());
        assertEquals(PaymentStatus.PENDING, order.getPaymentStatus());
        assertNull(result.getPayment());
        assertEquals(8, product.getStockQuantity());
        verify(cartItemRepository).deleteByCart(cart);
        verify(notificationService).sendNotification(eq(1L), any(), eq("ORDER"), any());
        verify(emailService).send(eq("alice@example.com"), anyString(), anyString());
        verify(notificationService).notifyAdmins(contains("New order #10"), eq("ORDER"), eq("/admin/orders"), isNull());
    }

    @Test
    void placeOrder_shouldRejectWhenNotEnoughStock() {
        User u = user();
        Product product = product(1);
        Cart cart = cartWith(u, product, 2);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> orderService.placeOrder(1L, request(PaymentMethod.CASH_ON_DELIVERY)));

        assertTrue(ex.getMessage().contains("Only 1"));
        assertEquals(1, product.getStockQuantity());
        verify(orderRepository, never()).save(any());
        verify(cartItemRepository, never()).deleteByCart(cart);
    }

    @Test
    void placeOrder_card_shouldFailWhenStripeIsNotConfigured() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user()));
        Cart cart = new Cart();
        CartItem ci = new CartItem();
        ci.setProduct(product(10));
        ci.setQuantity(1);
        when(cartRepository.findByUser_UserId(1L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCart(cart)).thenReturn(List.of(ci));
        when(stripeGateway.isConfigured()).thenReturn(false);

        assertThrows(BusinessRuleException.class,
                () -> orderService.placeOrder(1L, request(PaymentMethod.CARD)));
        verify(orderRepository, never()).save(any());
    }

    @Test
    void placeOrder_card_shouldReserveStockWithoutCallingStripe() {
        User u = user();
        Product product = product(10);
        cartWith(u, product, 2);
        saveAssignsId();
        when(stripeGateway.isConfigured()).thenReturn(true);

        PlaceOrderResponse result = orderService.placeOrder(1L, request(PaymentMethod.CARD));

        assertNull(result.getPayment());
        assertEquals(PaymentStatus.PENDING, result.getOrder().getPaymentStatus());
        assertEquals(8, product.getStockQuantity());
        verify(stripeGateway, never()).createPaymentIntent(any(Long.class), any(Long.class), any(), any());
        // Confirmation is only sent once the payment succeeds
        verify(notificationService, never()).sendNotification(any(), any(), any(), any());
        verify(emailService, never()).send(any(), any(), any());
    }

    @Test
    void startPayment_firstTime_createsPaymentForExactTotal() {
        Order order = unpaidCardOrder(user(), product(8));
        order.setStripePaymentIntentId(null);
        when(orderRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
        when(stripeGateway.publishableKey()).thenReturn("pk_test_123");
        when(stripeGateway.createPaymentIntent(
                eq(10L), eq(143000L), eq("alice@example.com"), eq("gmc-order-10-initial")))
                .thenReturn(intent("requires_payment_method", 143000L));

        PaymentSessionResponse session = orderService.startPayment(10L, 1L);

        assertEquals("pi_1_secret", session.getClientSecret());
        assertEquals("pk_test_123", session.getPublishableKey());
        assertEquals("pi_1", order.getStripePaymentIntentId());
    }

    // ════════════════════════ card payment ═══════════════════════════════════

    @Test
    void confirmPayment_shouldMarkPaidWhenStripeSaysSucceeded() {
        User u = user();
        Order order = unpaidCardOrder(u, product(8));
        when(orderRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
        when(stripeGateway.retrievePaymentIntent("pi_1")).thenReturn(intent("succeeded", 143000L));
        when(stripeGateway.currency()).thenReturn("lkr");

        OrderResponse result = orderService.confirmPayment(10L, 1L);

        assertEquals(PaymentStatus.COMPLETED, result.getPaymentStatus());
        assertNotNull(result.getPaidAt());
        verify(notificationService).sendNotification(eq(1L), any(), eq("PAYMENT"), eq("/orders/10"));
        verify(emailService).send(eq("alice@example.com"), anyString(), anyString());
        verify(notificationService).notifyAdmins(contains("Payment received: order #10"), eq("PAYMENT"),
                eq("/admin/orders"), isNull());
    }

    @Test
    void updateOrderStatus_shipped_shouldEmailCustomer_butProcessingShouldNot() {
        Order order = pendingOrder(user());
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        orderService.updateOrderStatus(1L, OrderStatus.PROCESSING);
        verify(emailService, never()).send(any(), any(), any());

        orderService.updateOrderStatus(1L, OrderStatus.SHIPPED);
        verify(emailService).send(eq("alice@example.com"), contains("on its way"), contains("on its way"));
    }


    @Test
    void confirmPayment_shouldStayPendingWhileCardStillNeeded() {
        Order order = unpaidCardOrder(user(), product(8));
        when(orderRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
        when(stripeGateway.retrievePaymentIntent("pi_1"))
                .thenReturn(intent("requires_payment_method", 143000L));

        assertEquals(PaymentStatus.PENDING, orderService.confirmPayment(10L, 1L).getPaymentStatus());
    }

    @Test
    void confirmPayment_shouldRejectOtherCustomersOrder() {
        User other = User.builder().userId(2L).name("Bob").build();
        when(orderRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(unpaidCardOrder(other, product(8))));

        assertThrows(ResourceNotFoundException.class, () -> orderService.confirmPayment(10L, 1L));
    }

    @Test
    void handleStripeWebhook_shouldMarkMatchingOrderPaid() {
        Order order = unpaidCardOrder(user(), product(8));
        when(stripeGateway.paymentIntentIdFromWebhook("payload", "sig")).thenReturn(Optional.of("pi_1"));
        when(orderRepository.findByStripePaymentIntentId("pi_1")).thenReturn(Optional.of(order));
        when(orderRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
        when(stripeGateway.retrievePaymentIntent("pi_1")).thenReturn(intent("succeeded", 143000L));
        when(stripeGateway.currency()).thenReturn("lkr");

        orderService.handleStripeWebhook("payload", "sig");

        assertEquals(PaymentStatus.COMPLETED, order.getPaymentStatus());
    }

    @Test
    void startPayment_shouldReuseOpenPayment() {
        Order order = unpaidCardOrder(user(), product(8));
        when(orderRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
        when(stripeGateway.retrievePaymentIntent("pi_1"))
                .thenReturn(intent("requires_payment_method", 143000L));

        PaymentSessionResponse session = orderService.startPayment(10L, 1L);

        assertEquals("pi_1_secret", session.getClientSecret());
        verify(stripeGateway, never()).createPaymentIntent(any(Long.class), any(Long.class), any(), any());
    }

    @Test
    void startPayment_shouldCreateNewPaymentWhenPreviousWasCancelled() {
        Order order = unpaidCardOrder(user(), product(8));
        when(orderRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
        when(stripeGateway.retrievePaymentIntent("pi_1")).thenReturn(intent("canceled", 143000L));
        when(stripeGateway.createPaymentIntent(eq(10L), eq(143000L), eq("alice@example.com"), anyString()))
                .thenReturn(new StripeGateway.Intent("pi_2", "requires_payment_method", 143000L, "lkr", "pi_2_secret"));

        PaymentSessionResponse session = orderService.startPayment(10L, 1L);

        assertEquals("pi_2_secret", session.getClientSecret());
        assertEquals("pi_2", order.getStripePaymentIntentId());
    }

    @Test
    void startPayment_shouldReportAlreadyPaidAndRecordIt() {
        Order order = unpaidCardOrder(user(), product(8));
        when(orderRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
        when(stripeGateway.retrievePaymentIntent("pi_1")).thenReturn(intent("succeeded", 143000L));
        when(stripeGateway.currency()).thenReturn("lkr");

        PaymentSessionResponse session = orderService.startPayment(10L, 1L);

        assertTrue(session.isAlreadyPaid());
        assertEquals(PaymentStatus.COMPLETED, order.getPaymentStatus());
    }

    @Test
    void startPayment_shouldRejectCashOnDeliveryOrder() {
        Order order = pendingOrder(user());
        order.setPaymentMethod(PaymentMethod.CASH_ON_DELIVERY);
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

        assertThrows(BusinessRuleException.class, () -> orderService.startPayment(1L, 1L));
    }

    @Test
    void expireUnpaidCardOrders_shouldCancelAndRestock() {
        Product product = product(8);
        Order order = unpaidCardOrder(user(), product);
        when(orderRepository.findByPaymentMethodAndStatusAndOrderDateBefore(
                eq(PaymentMethod.CARD), eq(OrderStatus.PENDING), any())).thenReturn(List.of(order));
        when(orderRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
        when(stripeGateway.retrievePaymentIntent("pi_1"))
                .thenReturn(intent("requires_payment_method", 143000L));

        int expired = orderService.expireUnpaidCardOrders();

        assertEquals(1, expired);
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertEquals(PaymentStatus.FAILED, order.getPaymentStatus());
        assertEquals(10, product.getStockQuantity());
        verify(stripeGateway).cancelPaymentIntent("pi_1");
    }

    @Test
    void expireUnpaidCardOrders_shouldKeepOrderPaidMeanwhile() {
        Product product = product(8);
        Order order = unpaidCardOrder(user(), product);
        when(orderRepository.findByPaymentMethodAndStatusAndOrderDateBefore(
                eq(PaymentMethod.CARD), eq(OrderStatus.PENDING), any())).thenReturn(List.of(order));
        when(orderRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
        when(stripeGateway.retrievePaymentIntent("pi_1")).thenReturn(intent("succeeded", 143000L));
        when(stripeGateway.currency()).thenReturn("lkr");

        assertEquals(0, orderService.expireUnpaidCardOrders());
        assertEquals(OrderStatus.PENDING, order.getStatus());
        assertEquals(PaymentStatus.COMPLETED, order.getPaymentStatus());
        assertEquals(8, product.getStockQuantity());
    }

    // ════════════════════════ queries ════════════════════════════════════════

    @Test
    void getOrderById_shouldThrowWhenOrderBelongsToDifferentUser() {
        User owner = User.builder().userId(2L).name("Bob").email("bob@example.com")
                .password("pass").role(Role.builder().roleName("CUSTOMER").build()).build();
        Order order = Order.builder().orderId(5L).user(owner).orderDate(LocalDateTime.now())
                .status(OrderStatus.PENDING).items(new ArrayList<>()).build();
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));

        assertThrows(ResourceNotFoundException.class, () -> orderService.getOrderById(5L, 1L));
    }

    @Test
    void getOrderById_shouldThrowWhenOrderNotFound() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> orderService.getOrderById(99L, 1L));
    }

    @Test
    void getOrdersByUser_shouldReturnUserOrders() {
        User u = user();
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));
        when(orderRepository.findByUser(u)).thenReturn(List.of(pendingOrder(u)));

        assertEquals(1, orderService.getOrdersByUser(1L).size());
    }

    @Test
    void getOrdersByUser_shouldThrowWhenUserNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> orderService.getOrdersByUser(99L));
    }

    @Test
    void getAllOrders_shouldReturnAllOrders() {
        when(orderRepository.findAll()).thenReturn(List.of(pendingOrder(user())));

        assertEquals(1, orderService.getAllOrders().size());
    }

    // ════════════════════════ admin status changes ═══════════════════════════

    @Test
    void updateOrderStatus_shouldThrowForDeliveredOrder() {
        Order delivered = Order.builder().orderId(1L).status(OrderStatus.DELIVERED)
                .user(user()).orderDate(LocalDateTime.now()).items(new ArrayList<>()).build();
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(delivered));

        assertThrows(InvalidOrderStatusTransitionException.class,
                () -> orderService.updateOrderStatus(1L, OrderStatus.PROCESSING));
    }

    @Test
    void updateOrderStatus_shouldThrowForCancelledOrder() {
        Order cancelled = Order.builder().orderId(1L).status(OrderStatus.CANCELLED)
                .user(user()).orderDate(LocalDateTime.now()).items(new ArrayList<>()).build();
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(cancelled));

        assertThrows(InvalidOrderStatusTransitionException.class,
                () -> orderService.updateOrderStatus(1L, OrderStatus.PROCESSING));
    }

    @Test
    void updateOrderStatus_shouldSucceedForNonTerminalOrder() {
        Order order = pendingOrder(user());
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse result = orderService.updateOrderStatus(1L, OrderStatus.PROCESSING);

        assertEquals(OrderStatus.PROCESSING, result.getStatus());
        verify(notificationService).sendNotification(eq(1L), any(), eq("ORDER"), any());
    }

    @Test
    void updateOrderStatus_shouldBlockProcessingUnpaidCardOrder() {
        Order order = unpaidCardOrder(user(), product(8));
        when(orderRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
        when(stripeGateway.retrievePaymentIntent("pi_1"))
                .thenReturn(intent("requires_payment_method", 143000L));

        assertThrows(BusinessRuleException.class,
                () -> orderService.updateOrderStatus(10L, OrderStatus.PROCESSING));
    }

    @Test
    void updateOrderStatus_cancel_shouldRestockAndCancelUnpaidPayment() {
        Product product = product(8);
        Order order = unpaidCardOrder(user(), product);
        when(orderRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse result = orderService.updateOrderStatus(10L, OrderStatus.CANCELLED);

        assertEquals(OrderStatus.CANCELLED, result.getStatus());
        assertEquals(10, product.getStockQuantity());
        verify(stripeGateway).cancelPaymentIntent("pi_1");
    }

    @Test
    void updateOrderStatus_deliveredCashOnDelivery_shouldMarkPaid() {
        Order order = pendingOrder(user());
        order.setStatus(OrderStatus.SHIPPED);
        order.setPaymentMethod(PaymentMethod.CASH_ON_DELIVERY);
        order.setPaymentStatus(PaymentStatus.PENDING);
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse result = orderService.updateOrderStatus(1L, OrderStatus.DELIVERED);

        assertEquals(PaymentStatus.COMPLETED, result.getPaymentStatus());
        assertNotNull(result.getPaidAt());
    }

    // ════════════════════════ status rules & refunds ═════════════════════════

    @Test
    void updateOrderStatus_cannotSkipSteps() {
        Order order = pendingOrder(user());
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

        assertThrows(BusinessRuleException.class, () -> orderService.updateOrderStatus(1L, OrderStatus.DELIVERED));
        verify(orderRepository, never()).save(any());
    }

    @Test
    void updateOrderStatus_cannotCancelAfterShipping() {
        Order order = pendingOrder(user());
        order.setStatus(OrderStatus.SHIPPED);
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

        assertThrows(BusinessRuleException.class, () -> orderService.updateOrderStatus(1L, OrderStatus.CANCELLED));
    }

    @Test
    void updateOrderStatus_sameStatusIsRejected() {
        Order order = pendingOrder(user());
        order.setStatus(OrderStatus.PROCESSING);
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

        assertThrows(BusinessRuleException.class, () -> orderService.updateOrderStatus(1L, OrderStatus.PROCESSING));
    }

    @Test
    void updateOrderStatus_cancellingPaidCardOrder_refundsThroughStripe() {
        Product product = product(8);
        Order order = unpaidCardOrder(user(), product);
        order.setPaymentStatus(PaymentStatus.COMPLETED);
        when(orderRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(stripeGateway.refundPaymentIntent("pi_1")).thenReturn(true);

        OrderResponse result = orderService.updateOrderStatus(10L, OrderStatus.CANCELLED);

        assertEquals(PaymentStatus.REFUNDED, result.getPaymentStatus());
        assertEquals(10, product.getStockQuantity());
        verify(notificationService).sendNotification(eq(1L), contains("has been refunded"), eq("ORDER"), any());
    }

    @Test
    void updateOrderStatus_failedRefundAlertsAdmins() {
        Order order = unpaidCardOrder(user(), product(8));
        order.setPaymentStatus(PaymentStatus.COMPLETED);
        when(orderRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(stripeGateway.refundPaymentIntent("pi_1")).thenReturn(false);

        OrderResponse result = orderService.updateOrderStatus(10L, OrderStatus.CANCELLED);

        assertEquals(PaymentStatus.COMPLETED, result.getPaymentStatus());
        verify(notificationService).notifyAdmins(contains("Automatic refund FAILED"), eq("PAYMENT"),
                eq("/admin/orders"), contains("Refund needed"));
    }

    @Test
    void paymentArrivingForCancelledOrder_isRefundedNotKept() {
        Order order = unpaidCardOrder(user(), product(8));
        order.setStatus(OrderStatus.CANCELLED);
        when(orderRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
        when(stripeGateway.retrievePaymentIntent("pi_1")).thenReturn(intent("succeeded", 143000L));
        when(stripeGateway.currency()).thenReturn("lkr");
        when(stripeGateway.refundPaymentIntent("pi_1")).thenReturn(true);

        OrderResponse result = orderService.confirmPayment(10L, 1L);

        assertEquals(PaymentStatus.REFUNDED, result.getPaymentStatus());
        verify(notificationService, never()).notifyAdmins(contains("Ready to process"), any(), any(), any());
    }

    @Test
    void paymentWithWrongAmount_isRefunded() {
        Order order = unpaidCardOrder(user(), product(8));
        when(orderRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
        when(stripeGateway.retrievePaymentIntent("pi_1")).thenReturn(intent("succeeded", 100L));
        when(stripeGateway.refundPaymentIntent("pi_1")).thenReturn(true);

        assertEquals(PaymentStatus.REFUNDED, orderService.confirmPayment(10L, 1L).getPaymentStatus());
    }

    @Test
    void placeOrder_rejectsArchivedProduct() {
        Product product = product(10);
        product.setIsActive(false);
        Cart cart = cartWith(user(), product, 1);

        assertThrows(BusinessRuleException.class,
                () -> orderService.placeOrder(1L, request(PaymentMethod.CASH_ON_DELIVERY)));
        verify(cartItemRepository, never()).deleteByCart(cart);
    }
}

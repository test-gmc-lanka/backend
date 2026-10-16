package com.gmc.backend.service.impl;

import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.function.Supplier;
import java.util.Map;
import com.gmc.backend.dto.request.PlaceOrderRequest;
import com.gmc.backend.dto.response.CheckoutSummaryResponse;
import com.gmc.backend.dto.response.OrderItemResponse;
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
import com.gmc.backend.model.User;
import com.gmc.backend.repository.CartItemRepository;
import com.gmc.backend.repository.CartRepository;
import com.gmc.backend.repository.OrderRepository;
import com.gmc.backend.repository.ProductRepository;
import com.gmc.backend.repository.UserRepository;
import com.gmc.backend.service.CheckoutPricing;
import com.gmc.backend.service.CheckoutPricing.PriceBreakdown;
import com.gmc.backend.service.EmailService;
import com.gmc.backend.service.NotificationService;
import com.gmc.backend.service.OrderService;
import com.gmc.backend.service.StripeGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final Set<OrderStatus> TERMINAL_STATUSES =
            EnumSet.of(OrderStatus.DELIVERED, OrderStatus.CANCELLED);

    // One step forward at a time; cancelling is only possible before the parcel leaves
    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = Map.of(
            OrderStatus.PENDING, EnumSet.of(OrderStatus.PROCESSING, OrderStatus.CANCELLED),
            OrderStatus.PROCESSING, EnumSet.of(OrderStatus.SHIPPED, OrderStatus.CANCELLED),
            OrderStatus.SHIPPED, EnumSet.of(OrderStatus.DELIVERED));

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final ProductServiceImpl productServiceImpl;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final CheckoutPricing checkoutPricing;
    private final StripeGateway stripeGateway;

    // Optional so unit tests can construct the service without Spring
    private TransactionTemplate transactionTemplate;

    @Autowired(required = false)
    void setTransactionManager(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = transactionManager == null ? null : new TransactionTemplate(transactionManager);
    }

    @Value("${app.checkout.unpaid-order-expiry-minutes:60}")
    private long unpaidOrderExpiryMinutes = 60;

    // ════════════════════════════ CHECKOUT ═════════════════════════════════

    @Override
    public CheckoutSummaryResponse getCheckoutSummary(Long userId) {
        List<CartItem> cartItems = cartRepository.findByUser_UserId(userId)
                .map(cartItemRepository::findByCart)
                .orElse(List.of());
        BigDecimal subtotal = cartItems.stream()
                .map(ci -> ci.getProduct().getPrice().multiply(BigDecimal.valueOf(ci.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        PriceBreakdown price = checkoutPricing.calculate(subtotal);
        boolean cardEnabled = stripeGateway.isConfigured();
        return CheckoutSummaryResponse.builder()
                .itemCount(cartItems.stream().mapToInt(CartItem::getQuantity).sum())
                .subtotal(price.subtotal())
                .shippingFee(price.shippingFee())
                .taxAmount(price.taxAmount())
                .taxRatePercent(checkoutPricing.taxRatePercent())
                .totalAmount(price.total())
                .currency(stripeGateway.currency().toUpperCase(Locale.ROOT))
                .cardPaymentsEnabled(cardEnabled)
                .stripePublishableKey(cardEnabled ? stripeGateway.publishableKey() : null)
                .build();
    }

    @Override
    @Transactional
    public PlaceOrderResponse placeOrder(Long userId, PlaceOrderRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        Cart cart = cartRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new BusinessRuleException("Cart is empty"));
        List<CartItem> cartItems = cartItemRepository.findByCart(cart);
        if (cartItems.isEmpty()) {
            throw new BusinessRuleException("Cart is empty");
        }
        PaymentMethod paymentMethod = request.getPaymentMethod();
        if (paymentMethod == null) {
            throw new BusinessRuleException("Choose a payment method");
        }
        if (paymentMethod == PaymentMethod.CARD && !stripeGateway.isConfigured()) {
            throw new BusinessRuleException("Card payments are not available right now. "
                    + "Please choose Cash on Delivery.");
        }

        OrderType orderType = (request.getOrderType() != null) ? request.getOrderType() : OrderType.B2C;
        Order newOrder = Order.builder()
                .orderDate(LocalDateTime.now())
                .status(OrderStatus.PENDING)
                .orderType(orderType)
                .notes(trimToNull(request.getNotes()))
                .shippingName(trimToNull(request.getShippingName()))
                .shippingPhone(trimToNull(request.getShippingPhone()))
                .shippingAddress(trimToNull(request.getShippingAddress()))
                .shippingCity(trimToNull(request.getShippingCity()))
                .shippingPostalCode(trimToNull(request.getShippingPostalCode()))
                .paymentMethod(paymentMethod)
                .paymentStatus(PaymentStatus.PENDING)
                .user(user)
                .build();

        // Reserve stock; row locks stop two checkouts from selling the same last unit
        List<OrderItem> orderItems = cartItems.stream().map(ci -> {
            Product product = lockProduct(ci.getProduct());
            if (Boolean.FALSE.equals(product.getIsActive())) {
                throw new BusinessRuleException(product.getName()
                        + " is no longer available. Please remove it from your cart.");
            }
            int available = product.getStockQuantity() == null ? 0 : product.getStockQuantity();
            if (available < ci.getQuantity()) {
                throw new BusinessRuleException(available == 0
                        ? product.getName() + " is out of stock. Please remove it from your cart."
                        : "Only " + available + " of " + product.getName() + " left in stock. "
                                + "Please reduce the quantity in your cart.");
            }
            product.setStockQuantity(available - ci.getQuantity());
            return OrderItem.builder()
                    .order(newOrder)
                    .product(product)
                    .quantity(ci.getQuantity())
                    .unitPrice(product.getPrice())
                    .build();
        }).collect(Collectors.toList());
        newOrder.setItems(orderItems);

        BigDecimal subtotal = orderItems.stream()
                .map(oi -> oi.getUnitPrice().multiply(BigDecimal.valueOf(oi.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        PriceBreakdown price = checkoutPricing.calculate(subtotal);
        newOrder.setSubtotal(price.subtotal());
        newOrder.setShippingFee(price.shippingFee());
        newOrder.setTaxAmount(price.taxAmount());
        newOrder.setTotalAmount(price.total());

        Order saved = orderRepository.save(newOrder);
        cartItemRepository.deleteByCart(cart);

        // Card orders: the browser asks for the card form next (startPayment). Creating the Stripe
        // payment there keeps slow network calls out of this transaction, which holds stock locks.
        if (paymentMethod == PaymentMethod.CASH_ON_DELIVERY) {
            notificationService.sendNotification(userId, "Your order #" + saved.getOrderId()
                    + " has been placed. Please keep " + formatMoney(saved.getTotalAmount())
                    + " ready to pay on delivery.", "ORDER", customerLink(saved));
            sendOrderConfirmationEmail(saved);
            notificationService.notifyAdmins("New order #" + saved.getOrderId() + " from " + user.getName()
                    + " — " + formatMoney(saved.getTotalAmount()) + ", cash on delivery.",
                    "ORDER", ADMIN_ORDERS_LINK, null);
        }
        return PlaceOrderResponse.builder().order(toResponse(saved)).build();
    }

    // ════════════════════════════ CARD PAYMENT ═════════════════════════════

    @Override
    @Transactional
    public PaymentSessionResponse startPayment(Long orderId, Long userId) {
        Order order = findOwnedOrder(orderId, userId, true);
        if (order.getPaymentMethod() != PaymentMethod.CARD) {
            throw new BusinessRuleException("This order is paid on delivery");
        }
        if (order.getPaymentStatus() == PaymentStatus.COMPLETED) {
            throw new BusinessRuleException("This order is already paid");
        }
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new BusinessRuleException("This order was cancelled. Please place a new order.");
        }
        if (order.getStripePaymentIntentId() != null) {
            StripeGateway.Intent intent = stripeGateway.retrievePaymentIntent(order.getStripePaymentIntentId());
            if (intent.succeeded()) {
                // Paid while the webhook was on its way: record it (no exception, or it would roll back)
                applyIntent(order, intent);
                return PaymentSessionResponse.builder().orderId(order.getOrderId()).alreadyPaid(true).build();
            }
            if (!intent.canceled()) {
                return paymentSession(order, intent.clientSecret());
            }
        }
        // No usable payment yet (or the previous one was cancelled): start a new one.
        // The key is stable per intent id, so a retried request doesn't create a second payment.
        String attempt = order.getStripePaymentIntentId() == null ? "initial" : order.getStripePaymentIntentId();
        StripeGateway.Intent intent = stripeGateway.createPaymentIntent(order.getOrderId(),
                toMinorUnits(order.getTotalAmount()), order.getUser().getEmail(),
                "gmc-order-" + order.getOrderId() + "-" + attempt);
        order.setStripePaymentIntentId(intent.id());
        order.setPaymentStatus(PaymentStatus.PENDING);
        orderRepository.save(order);
        return paymentSession(order, intent.clientSecret());
    }

    @Override
    @Transactional
    public OrderResponse confirmPayment(Long orderId, Long userId) {
        Order order = findOwnedOrder(orderId, userId, true);
        // Never trust the browser: ask Stripe what actually happened
        syncWithStripe(order);
        return toResponse(order);
    }

    @Override
    @Transactional
    public void handleStripeWebhook(String payload, String signatureHeader) {
        Optional<String> intentId = stripeGateway.paymentIntentIdFromWebhook(payload, signatureHeader);
        intentId.flatMap(orderRepository::findByStripePaymentIntentId)
                .flatMap(o -> orderRepository.findByIdForUpdate(o.getOrderId()))
                .ifPresent(this::syncWithStripe);
    }

    // Not @Transactional: each order is expired in its own short transaction, so one slow Stripe
    // call or failure doesn't hold locks on (or roll back) every other expired order
    @Override
    public int expireUnpaidCardOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(unpaidOrderExpiryMinutes);
        int expired = 0;
        for (Order candidate : orderRepository.findByPaymentMethodAndStatusAndOrderDateBefore(
                PaymentMethod.CARD, OrderStatus.PENDING, cutoff)) {
            try {
                Boolean done = inTransaction(() -> expireIfStillUnpaid(candidate.getOrderId()));
                if (Boolean.TRUE.equals(done)) {
                    expired++;
                }
            } catch (RuntimeException e) {
                log.warn("Skipping expiry of order {}: {}", candidate.getOrderId(), e.getMessage());
            }
        }
        return expired;
    }

    private boolean expireIfStillUnpaid(Long orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.PENDING
                || order.getPaymentStatus() == PaymentStatus.COMPLETED) {
            return false;
        }
        syncWithStripe(order); // the customer may have paid while the webhook was delayed
        if (order.getPaymentStatus() == PaymentStatus.COMPLETED) {
            return false;
        }
        order.setStatus(OrderStatus.CANCELLED);
        order.setPaymentStatus(PaymentStatus.FAILED);
        restock(order);
        stripeGateway.cancelPaymentIntent(order.getStripePaymentIntentId());
        orderRepository.save(order);
        notifyCustomer(order, "Order #" + order.getOrderId() + " cancelled",
                "Order #" + order.getOrderId() + " was cancelled because payment wasn't completed within "
                        + unpaidOrderExpiryMinutes + " minutes. Your items are back in stock if you'd "
                        + "like to order them again.", true);
        return true;
    }

    private <T> T inTransaction(Supplier<T> work) {
        return transactionTemplate == null ? work.get() : transactionTemplate.execute(status -> work.get());
    }

    // Updates the order from the payment's real state in Stripe
    private void syncWithStripe(Order order) {
        if (order.getStripePaymentIntentId() == null || order.getPaymentStatus() == PaymentStatus.COMPLETED
                || order.getPaymentStatus() == PaymentStatus.REFUNDED) {
            return;
        }
        applyIntent(order, stripeGateway.retrievePaymentIntent(order.getStripePaymentIntentId()));
    }

    private void applyIntent(Order order, StripeGateway.Intent intent) {
        if (order.getPaymentStatus() == PaymentStatus.COMPLETED || order.getPaymentStatus() == PaymentStatus.REFUNDED) {
            return;
        }
        if (intent.succeeded()) {
            boolean amountMatches = intent.amountMinor() == toMinorUnits(order.getTotalAmount())
                    && stripeGateway.currency().equalsIgnoreCase(intent.currency());
            if (!amountMatches || order.getStatus() == OrderStatus.CANCELLED) {
                // Money arrived for an order we can't fulfil: give it back instead of keeping it
                refundUnfulfillablePayment(order, intent, amountMatches
                        ? "the order had already been cancelled"
                        : "the amount charged didn't match the order total");
                return;
            }
            order.setPaymentStatus(PaymentStatus.COMPLETED);
            order.setPaidAt(LocalDateTime.now());
            orderRepository.save(order);
            notificationService.sendNotification(order.getUser().getUserId(), "Payment of "
                    + formatMoney(order.getTotalAmount()) + " received for order #"
                    + order.getOrderId() + ". We'll start preparing it soon.", "PAYMENT", customerLink(order));
            sendOrderConfirmationEmail(order);
            notificationService.notifyAdmins("Payment received: order #" + order.getOrderId() + " from "
                    + order.getUser().getName() + " — " + formatMoney(order.getTotalAmount())
                    + " paid by card. Ready to process.", "PAYMENT", ADMIN_ORDERS_LINK, null);
        } else if (intent.canceled()) {
            order.setPaymentStatus(PaymentStatus.FAILED);
            orderRepository.save(order);
        }
    }

    private void refundUnfulfillablePayment(Order order, StripeGateway.Intent intent, String reason) {
        log.error("Refunding payment {} for order {}: {}", intent.id(), order.getOrderId(), reason);
        boolean refunded = stripeGateway.refundPaymentIntent(intent.id());
        if (refunded) {
            order.setPaymentStatus(PaymentStatus.REFUNDED);
            orderRepository.save(order);
            notifyCustomer(order, "Payment refunded for order #" + order.getOrderId(),
                    "We received your card payment for order #" + order.getOrderId() + ", but " + reason
                            + ", so it has been refunded in full. Refunds usually appear within 5–10 business days.",
                    true);
        }
        notificationService.notifyAdmins("Card payment for order #" + order.getOrderId() + " needs attention: "
                + reason + ". " + (refunded ? "It was refunded automatically."
                        : "Automatic refund FAILED — refund it in the Stripe Dashboard."),
                "PAYMENT", ADMIN_ORDERS_LINK, refunded ? null : "Refund needed for order #" + order.getOrderId());
    }

    // ════════════════════════════ QUERIES ═════════════════════════════════

    @Override
    public OrderResponse getOrderById(Long orderId, Long userId) {
        return toResponse(findOwnedOrder(orderId, userId, false));
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

    // ════════════════════════════ ADMIN ═══════════════════════════════════

    @Override
    @Transactional
    public OrderResponse updateOrderStatus(Long orderId, OrderStatus newStatus) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        if (TERMINAL_STATUSES.contains(order.getStatus())) {
            throw new InvalidOrderStatusTransitionException(order.getStatus(), newStatus);
        }
        if (order.getStatus() == newStatus) {
            throw new BusinessRuleException("Order #" + orderId + " is already " + newStatus);
        }
        if (!ALLOWED_TRANSITIONS.getOrDefault(order.getStatus(), Set.of()).contains(newStatus)) {
            throw new BusinessRuleException("Order #" + orderId + " can't go from " + order.getStatus()
                    + " to " + newStatus + ". Orders move Pending → Processing → Shipped → Delivered, "
                    + "and can only be cancelled before shipping.");
        }
        boolean unpaidCard = order.getPaymentMethod() == PaymentMethod.CARD
                && order.getPaymentStatus() != PaymentStatus.COMPLETED;
        if (unpaidCard && newStatus != OrderStatus.CANCELLED) {
            syncWithStripe(order); // in case the payment webhook hasn't arrived yet
            if (order.getPaymentStatus() != PaymentStatus.COMPLETED) {
                throw new BusinessRuleException("Order #" + orderId + " hasn't been paid by card yet. "
                        + "It can only be cancelled until the customer pays.");
            }
        }

        String message = statusMessage(orderId, newStatus);
        if (newStatus == OrderStatus.CANCELLED) {
            restock(order);
            if (unpaidCard) {
                stripeGateway.cancelPaymentIntent(order.getStripePaymentIntentId());
            } else if (order.getPaymentMethod() == PaymentMethod.CARD) {
                if (stripeGateway.refundPaymentIntent(order.getStripePaymentIntentId())) {
                    order.setPaymentStatus(PaymentStatus.REFUNDED);
                    message += " Your card payment has been refunded and should appear within 5–10 business days.";
                } else {
                    message += " We'll refund your card payment shortly.";
                    notificationService.notifyAdmins("Automatic refund FAILED for cancelled order #" + orderId
                            + " (" + formatMoney(order.getTotalAmount()) + "). Refund it in the Stripe Dashboard.",
                            "PAYMENT", ADMIN_ORDERS_LINK, "Refund needed for order #" + orderId);
                }
            }
        }
        if (newStatus == OrderStatus.DELIVERED && order.getPaymentMethod() == PaymentMethod.CASH_ON_DELIVERY) {
            order.setPaymentStatus(PaymentStatus.COMPLETED); // cash collected by the courier
            order.setPaidAt(LocalDateTime.now());
        }
        order.setStatus(newStatus);
        Order saved = orderRepository.save(order);
        boolean email = newStatus == OrderStatus.SHIPPED || newStatus == OrderStatus.DELIVERED
                || newStatus == OrderStatus.CANCELLED;
        notifyCustomer(saved, "Order #" + orderId + " " + statusTitle(newStatus), message, email);
        return toResponse(saved);
    }

    // ════════════════════════════ HELPERS ═════════════════════════════════

    private static final String ADMIN_ORDERS_LINK = "/admin/orders";

    private static String customerLink(Order order) {
        return "/orders/" + order.getOrderId();
    }

    // In-app always; email only for milestones (shipped, delivered, cancelled)
    private void notifyCustomer(Order order, String subject, String message, boolean email) {
        User user = order.getUser();
        notificationService.sendNotification(user.getUserId(), message, "ORDER", customerLink(order));
        if (email) {
            emailService.send(user.getEmail(), "GMC Lanka – " + subject,
                    "Hi " + user.getName() + ",\n\n" + message
                            + "\n\nView your order any time in your GMC Lanka account.\n\n— GMC Lanka");
        }
    }

    // OrderStatus.SHIPPED is shown to customers as "completed and on its way"
    private static String statusMessage(Long orderId, OrderStatus status) {
        return switch (status) {
            case PENDING -> "Order #" + orderId + " is pending. We'll update you soon.";
            case PROCESSING -> "Order #" + orderId + " is being prepared.";
            case SHIPPED -> "Good news! Order #" + orderId + " is ready and on its way to you.";
            case DELIVERED -> "Order #" + orderId + " has been delivered. Thank you for shopping with GMC Lanka! "
                    + "We'd love your review — rate your items from the order page.";
            case CANCELLED -> "Order #" + orderId + " was cancelled.";
        };
    }

    private static String statusTitle(OrderStatus status) {
        return switch (status) {
            case SHIPPED -> "is on its way";
            case DELIVERED -> "delivered";
            case CANCELLED -> "cancelled";
            default -> "update";
        };
    }

    private Order findOwnedOrder(Long orderId, Long userId, boolean lock) {
        Order order = (lock ? orderRepository.findByIdForUpdate(orderId) : orderRepository.findById(orderId))
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        if (!order.getUser().getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Order not found: " + orderId);
        }
        return order;
    }

    private Product lockProduct(Product product) {
        return productRepository.findByIdForUpdate(product.getProductId()).orElse(product);
    }

    // Puts the order's items back into stock (cancellation / payment expiry)
    private void restock(Order order) {
        for (OrderItem item : order.getItems()) {
            Product product = lockProduct(item.getProduct());
            int stock = product.getStockQuantity() == null ? 0 : product.getStockQuantity();
            product.setStockQuantity(stock + item.getQuantity());
        }
    }

    private PaymentSessionResponse paymentSession(Order order, String clientSecret) {
        return PaymentSessionResponse.builder()
                .orderId(order.getOrderId())
                .clientSecret(clientSecret)
                .publishableKey(stripeGateway.publishableKey())
                .build();
    }

    // Stripe takes amounts in the smallest currency unit: LKR 1,500.00 -> 150000
    private static long toMinorUnits(BigDecimal amount) {
        return amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private static String formatMoney(BigDecimal amount) {
        return String.format(Locale.ENGLISH, "LKR %,.2f", amount);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void sendOrderConfirmationEmail(Order order) {
        User user = order.getUser();
        StringBuilder body = new StringBuilder()
                .append("Hi ").append(user.getName()).append(",\n\n")
                .append("Thank you for your order! Here are the details.\n\n")
                .append("Order #").append(order.getOrderId()).append('\n');
        for (OrderItem item : order.getItems()) {
            body.append("  • ").append(item.getProduct().getName()).append(" × ").append(item.getQuantity())
                    .append("  ").append(formatMoney(item.getUnitPrice()
                            .multiply(BigDecimal.valueOf(item.getQuantity())))).append('\n');
        }
        body.append("\nSubtotal: ").append(formatMoney(order.getSubtotal()))
                .append("\nShipping: ").append(formatMoney(order.getShippingFee()))
                .append("\nVAT: ").append(formatMoney(order.getTaxAmount()))
                .append("\nTotal: ").append(formatMoney(order.getTotalAmount()))
                .append("\n\nPayment: ").append(order.getPaymentMethod() == PaymentMethod.CARD
                        ? "Paid by card" : "Cash on delivery")
                .append("\nDeliver to: ").append(order.getShippingName()).append(", ")
                .append(order.getShippingAddress()).append(", ").append(order.getShippingCity())
                .append(order.getShippingPostalCode() == null ? "" : " " + order.getShippingPostalCode())
                .append("\nPhone: ").append(order.getShippingPhone())
                .append("\n\nYou can track your order any time in your GMC Lanka account.\n\n— GMC Lanka");
        emailService.send(user.getEmail(), "GMC Lanka – Order #" + order.getOrderId() + " confirmed",
                body.toString());
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
                .subtotal(order.getSubtotal())
                .shippingFee(order.getShippingFee())
                .taxAmount(order.getTaxAmount())
                .notes(order.getNotes())
                .userId(order.getUser().getUserId())
                .userName(order.getUser().getName())
                .items(items)
                .shippingName(order.getShippingName())
                .shippingPhone(order.getShippingPhone())
                .shippingAddress(order.getShippingAddress())
                .shippingCity(order.getShippingCity())
                .shippingPostalCode(order.getShippingPostalCode())
                .paymentMethod(order.getPaymentMethod())
                .paymentStatus(order.getPaymentStatus())
                .paidAt(order.getPaidAt())
                .build();
    }
}

package com.gmc.backend.service;

import com.gmc.backend.dto.request.PlaceOrderRequest;
import com.gmc.backend.dto.response.CheckoutSummaryResponse;
import com.gmc.backend.dto.response.OrderResponse;
import com.gmc.backend.dto.response.PaymentSessionResponse;
import com.gmc.backend.dto.response.PlaceOrderResponse;
import com.gmc.backend.model.OrderStatus;
import java.util.List;

public interface OrderService {

    // ── Checkout ──
    CheckoutSummaryResponse getCheckoutSummary(Long userId);

    PlaceOrderResponse placeOrder(Long userId, PlaceOrderRequest request);

    // ── Card payment (Stripe) ──
    /** Card form details for an unpaid card order (used by "Pay now" and retries). */
    PaymentSessionResponse startPayment(Long orderId, Long userId);

    /** Re-checks the payment with Stripe after the customer pays and returns the updated order. */
    OrderResponse confirmPayment(Long orderId, Long userId);

    void handleStripeWebhook(String payload, String signatureHeader);

    /** Cancels card orders that stayed unpaid too long and releases their stock. */
    int expireUnpaidCardOrders();

    // ── Queries ──
    OrderResponse getOrderById(Long orderId, Long userId);

    List<OrderResponse> getOrdersByUser(Long userId);

    List<OrderResponse> getAllOrders();

    List<OrderResponse> getOrdersByStatus(OrderStatus status);

    // ── Admin ──
    OrderResponse updateOrderStatus(Long orderId, OrderStatus newStatus);
}

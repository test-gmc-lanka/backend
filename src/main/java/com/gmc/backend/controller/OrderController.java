package com.gmc.backend.controller;

import com.gmc.backend.dto.request.PlaceOrderRequest;
import com.gmc.backend.dto.response.CheckoutSummaryResponse;
import com.gmc.backend.dto.response.OrderResponse;
import com.gmc.backend.dto.response.PaymentSessionResponse;
import com.gmc.backend.dto.response.PlaceOrderResponse;
import com.gmc.backend.security.CustomUserDetails;
import com.gmc.backend.service.OrderService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    // Exact price breakdown for the current cart, shown on the checkout page
    @GetMapping("/checkout-summary")
    public ResponseEntity<CheckoutSummaryResponse> getCheckoutSummary(@AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(orderService.getCheckoutSummary(user.getId()));
    }

    @PostMapping
    public ResponseEntity<PlaceOrderResponse> placeOrder(
            @AuthenticationPrincipal CustomUserDetails user, @Valid @RequestBody PlaceOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.placeOrder(user.getId(), request));
    }

    @GetMapping
    public ResponseEntity<List<OrderResponse>> getMyOrders(@AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(orderService.getOrdersByUser(user.getId()));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.getOrderById(orderId, user.getId()));
    }

    // Card form details for an unpaid card order ("Pay now" / retry)
    @PostMapping("/{orderId}/payment")
    public ResponseEntity<PaymentSessionResponse> startPayment(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.startPayment(orderId, user.getId()));
    }

    // Called after Stripe finishes; the server checks the result with Stripe itself
    @PostMapping("/{orderId}/payment/confirm")
    public ResponseEntity<OrderResponse> confirmPayment(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.confirmPayment(orderId, user.getId()));
    }
}

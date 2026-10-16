package com.gmc.backend.controller;

import com.gmc.backend.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final OrderService orderService;

    /**
     * Stripe calls this when a payment succeeds or fails (configured in the Stripe Dashboard
     * or forwarded by the Stripe CLI). It is public, so the Stripe-Signature header is verified.
     */
    @PostMapping("/stripe/webhook")
    public ResponseEntity<String> stripeWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        if (signature == null || signature.isBlank()) {
            return ResponseEntity.badRequest().body("Missing Stripe-Signature header");
        }
        try {
            orderService.handleStripeWebhook(payload, signature);
            return ResponseEntity.ok("received");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid signature");
        } catch (IllegalStateException e) {
            log.warn("Stripe webhook received but stripe.webhook-secret is not set");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Webhook not configured");
        }
    }
}

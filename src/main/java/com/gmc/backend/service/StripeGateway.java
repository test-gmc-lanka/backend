package com.gmc.backend.service;

import java.util.Optional;

/** Thin wrapper around the Stripe API so the order logic can be unit-tested without Stripe. */
public interface StripeGateway {

    /** True when stripe.secret-key and stripe.publishable-key are set. */
    boolean isConfigured();

    String publishableKey();

    String currency();

    /** amountMinor is in the smallest currency unit (cents), e.g. LKR 1,500.00 -> 150000. */
    Intent createPaymentIntent(long orderId, long amountMinor, String customerEmail, String idempotencyKey);

    Intent retrievePaymentIntent(String paymentIntentId);

    /** Best effort; used when an unpaid order is cancelled. */
    void cancelPaymentIntent(String paymentIntentId);

    /** Refunds a succeeded payment in full. Returns false if Stripe refused or was unreachable. */
    boolean refundPaymentIntent(String paymentIntentId);

    /**
     * Verifies the Stripe-Signature header and returns the payment intent id for
     * payment_intent.* events (empty for other events).
     * Throws IllegalArgumentException when the signature is invalid.
     */
    Optional<String> paymentIntentIdFromWebhook(String payload, String signatureHeader);

    /** Status values are Stripe's: succeeded, processing, requires_payment_method, canceled, ... */
    record Intent(String id, String status, long amountMinor, String currency, String clientSecret) {

        public boolean succeeded() {
            return "succeeded".equals(status);
        }

        public boolean canceled() {
            return "canceled".equals(status);
        }
    }
}

package com.gmc.backend.service.impl;

import com.stripe.param.RefundCreateParams;
import com.stripe.model.Refund;
import com.gmc.backend.exception.BusinessRuleException;
import com.gmc.backend.service.StripeGateway;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;

@Slf4j
@Service
public class StripeGatewayImpl implements StripeGateway {

    private final String secretKey;
    private final String publishableKey;
    private final String webhookSecret;
    private final String currency;

    public StripeGatewayImpl(
            @Value("${stripe.secret-key:}") String secretKey,
            @Value("${stripe.publishable-key:}") String publishableKey,
            @Value("${stripe.webhook-secret:}") String webhookSecret,
            @Value("${stripe.currency:lkr}") String currency) {
        this.secretKey = secretKey.trim();
        this.publishableKey = publishableKey.trim();
        this.webhookSecret = webhookSecret.trim();
        this.currency = currency.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public boolean isConfigured() {
        return secretKey.startsWith("sk_") && publishableKey.startsWith("pk_");
    }

    @Override
    public String publishableKey() {
        return publishableKey;
    }

    @Override
    public String currency() {
        return currency;
    }

    @Override
    public Intent createPaymentIntent(long orderId, long amountMinor, String customerEmail,
                                      String idempotencyKey) {
        requireConfigured();
        PaymentIntentCreateParams.Builder params = PaymentIntentCreateParams.builder()
                .setAmount(amountMinor)
                .setCurrency(currency)
                .setDescription("GMC Lanka order #" + orderId)
                .putMetadata("orderId", String.valueOf(orderId))
                // Lets Stripe offer every payment method enabled in the Dashboard (cards, wallets, ...)
                .setAutomaticPaymentMethods(PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                        .setEnabled(true)
                        .build());
        if (customerEmail != null && !customerEmail.isBlank()) {
            params.setReceiptEmail(customerEmail);
        }
        try {
            return toIntent(PaymentIntent.create(params.build(), options(idempotencyKey)));
        } catch (StripeException e) {
            log.error("Stripe could not create a payment for order {}: {}", orderId, e.getMessage());
            throw new BusinessRuleException("Card payment could not be started: " + e.getUserMessage());
        }
    }

    @Override
    public Intent retrievePaymentIntent(String paymentIntentId) {
        requireConfigured();
        try {
            return toIntent(PaymentIntent.retrieve(paymentIntentId, options(null)));
        } catch (StripeException e) {
            log.error("Stripe could not load payment {}: {}", paymentIntentId, e.getMessage());
            throw new BusinessRuleException("Could not check the payment with Stripe. Please try again.");
        }
    }

    @Override
    public void cancelPaymentIntent(String paymentIntentId) {
        if (!isConfigured() || paymentIntentId == null) {
            return;
        }
        try {
            PaymentIntent intent = PaymentIntent.retrieve(paymentIntentId, options(null));
            if (!"succeeded".equals(intent.getStatus()) && !"canceled".equals(intent.getStatus())) {
                intent.cancel(options(null));
            }
        } catch (StripeException e) {
            log.warn("Could not cancel Stripe payment {}: {}", paymentIntentId, e.getMessage());
        }
    }

    @Override
    public boolean refundPaymentIntent(String paymentIntentId) {
        if (!isConfigured() || paymentIntentId == null) {
            return false;
        }
        try {
            Refund.create(RefundCreateParams.builder()
                    .setPaymentIntent(paymentIntentId)
                    .setReason(RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER)
                    .build(), options("refund-" + paymentIntentId));
            return true;
        } catch (StripeException e) {
            log.error("Stripe refund failed for {}: {}", paymentIntentId, e.getMessage());
            return false;
        }
    }

    @Override
    public Optional<String> paymentIntentIdFromWebhook(String payload, String signatureHeader) {
        if (webhookSecret.isEmpty()) {
            throw new IllegalStateException("stripe.webhook-secret is not configured");
        }
        Event event;
        try {
            event = Webhook.constructEvent(payload, signatureHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            throw new IllegalArgumentException("Invalid Stripe signature", e);
        }
        if (event.getType() == null || !event.getType().startsWith("payment_intent.")) {
            return Optional.empty();
        }
        try {
            // deserializeUnsafe also works when the event's API version differs from the library's
            StripeObject object = event.getDataObjectDeserializer().deserializeUnsafe();
            return object instanceof PaymentIntent intent ? Optional.of(intent.getId()) : Optional.empty();
        } catch (Exception e) {
            log.warn("Could not read Stripe event {}: {}", event.getId(), e.getMessage());
            return Optional.empty();
        }
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new BusinessRuleException("Card payments are not available right now. "
                    + "Please choose Cash on Delivery.");
        }
    }

    private RequestOptions options(String idempotencyKey) {
        // Short timeouts so a slow Stripe response can't hold requests (or DB locks) for long
        RequestOptions.RequestOptionsBuilder builder = RequestOptions.builder().setApiKey(secretKey)
                .setConnectTimeout(10_000)
                .setReadTimeout(20_000)
                .setMaxNetworkRetries(1);
        if (idempotencyKey != null) {
            builder.setIdempotencyKey(idempotencyKey);
        }
        return builder.build();
    }

    private static Intent toIntent(PaymentIntent pi) {
        return new Intent(pi.getId(), pi.getStatus(),
                pi.getAmount() == null ? 0 : pi.getAmount(), pi.getCurrency(), pi.getClientSecret());
    }
}

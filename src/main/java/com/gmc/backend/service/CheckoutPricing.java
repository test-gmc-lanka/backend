package com.gmc.backend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The single place where order totals are calculated. The checkout page displays these
 * numbers and card payments charge exactly totalAmount, so they can never disagree.
 */
@Component
public class CheckoutPricing {

    private final BigDecimal taxRate;
    private final BigDecimal shippingFee;
    private final BigDecimal freeShippingThreshold;

    public CheckoutPricing(
            @Value("${app.checkout.tax-rate:0.15}") BigDecimal taxRate,
            @Value("${app.checkout.shipping-fee:1200}") BigDecimal shippingFee,
            @Value("${app.checkout.free-shipping-threshold:0}") BigDecimal freeShippingThreshold) {
        this.taxRate = taxRate;
        this.shippingFee = shippingFee;
        this.freeShippingThreshold = freeShippingThreshold;
    }

    public PriceBreakdown calculate(BigDecimal subtotal) {
        BigDecimal items = subtotal.setScale(2, RoundingMode.HALF_UP);
        // A threshold of 0 means shipping is always charged
        boolean freeShipping = freeShippingThreshold.signum() > 0 && items.compareTo(freeShippingThreshold) >= 0;
        BigDecimal shipping =
                freeShipping ? BigDecimal.ZERO.setScale(2) : shippingFee.setScale(2, RoundingMode.HALF_UP);
        BigDecimal tax = items.multiply(taxRate).setScale(2, RoundingMode.HALF_UP);
        return new PriceBreakdown(items, shipping, tax, items.add(shipping).add(tax));
    }

    public BigDecimal taxRatePercent() {
        return taxRate.movePointRight(2).stripTrailingZeros();
    }

    public record PriceBreakdown(BigDecimal subtotal, BigDecimal shippingFee, BigDecimal taxAmount, BigDecimal total) {}
}

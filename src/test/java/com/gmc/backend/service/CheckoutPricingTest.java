package com.gmc.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.gmc.backend.service.CheckoutPricing.PriceBreakdown;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CheckoutPricingTest {

    @Test
    void calculate_addsFlatShippingAndVatOnItems() {
        CheckoutPricing pricing = new CheckoutPricing(new BigDecimal("0.15"), new BigDecimal("1200"), BigDecimal.ZERO);

        PriceBreakdown p = pricing.calculate(new BigDecimal("1000"));

        assertEquals(new BigDecimal("1000.00"), p.subtotal());
        assertEquals(new BigDecimal("1200.00"), p.shippingFee());
        assertEquals(new BigDecimal("150.00"), p.taxAmount());
        assertEquals(new BigDecimal("2350.00"), p.total());
    }

    @Test
    void calculate_roundsVatToCents() {
        CheckoutPricing pricing = new CheckoutPricing(new BigDecimal("0.15"), BigDecimal.ZERO, BigDecimal.ZERO);

        assertEquals(
                new BigDecimal("15.02"),
                pricing.calculate(new BigDecimal("100.10")).taxAmount());
    }

    @Test
    void calculate_freeShippingFromThreshold() {
        CheckoutPricing pricing =
                new CheckoutPricing(new BigDecimal("0.15"), new BigDecimal("1200"), new BigDecimal("50000"));

        assertEquals(
                new BigDecimal("1200.00"),
                pricing.calculate(new BigDecimal("49999.99")).shippingFee());
        assertEquals(
                new BigDecimal("0.00"),
                pricing.calculate(new BigDecimal("50000")).shippingFee());
    }

    @Test
    void taxRatePercent_isHumanReadable() {
        CheckoutPricing pricing = new CheckoutPricing(new BigDecimal("0.15"), BigDecimal.ZERO, BigDecimal.ZERO);

        assertEquals(0, new BigDecimal("15").compareTo(pricing.taxRatePercent()));
    }
}

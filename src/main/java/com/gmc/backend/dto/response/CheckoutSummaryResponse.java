package com.gmc.backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/** What the checkout page shows: the exact amounts the order will be created (and charged) with. */
@Data
@Builder
public class CheckoutSummaryResponse {

    private int itemCount;
    private BigDecimal subtotal;
    private BigDecimal shippingFee;
    private BigDecimal taxAmount;
    private BigDecimal taxRatePercent;
    private BigDecimal totalAmount;
    private String currency;

    private boolean cardPaymentsEnabled;
    private String stripePublishableKey;
}

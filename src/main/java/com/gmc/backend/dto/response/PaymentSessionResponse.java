package com.gmc.backend.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * Lets the browser show Stripe's card form for an order. The client secret only allows
 * confirming this one payment; it cannot be used to read or change anything else.
 */
@Data
@Builder
public class PaymentSessionResponse {

    private Long orderId;
    private String clientSecret;
    private String publishableKey;

    // True when Stripe reports the order was already paid; no card form is needed
    private boolean alreadyPaid;
}

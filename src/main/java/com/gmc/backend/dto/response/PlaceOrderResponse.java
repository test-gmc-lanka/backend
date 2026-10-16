package com.gmc.backend.dto.response;

import lombok.Builder;
import lombok.Data;

/** Result of checkout; payment is set only for card orders that still need to be paid. */
@Data
@Builder
public class PlaceOrderResponse {

    private OrderResponse order;
    private PaymentSessionResponse payment;
}

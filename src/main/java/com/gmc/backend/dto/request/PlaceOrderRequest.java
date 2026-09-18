package com.gmc.backend.dto.request;

import com.gmc.backend.model.OrderType;
import lombok.Data;

@Data
public class PlaceOrderRequest {

    private OrderType orderType = OrderType.B2C;

    private String notes;
}

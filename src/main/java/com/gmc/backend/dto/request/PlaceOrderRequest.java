package com.gmc.backend.dto.request;

import com.gmc.backend.model.OrderType;
import com.gmc.backend.model.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PlaceOrderRequest {

    private OrderType orderType = OrderType.B2C;

    @Size(max = 1000, message = "Notes must be at most 1000 characters")
    private String notes;

    // ── Delivery details ──
    @NotBlank(message = "Recipient name is required")
    @Size(max = 255, message = "Recipient name is too long")
    private String shippingName;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^\\+?[0-9 ()-]{9,20}$", message = "Enter a valid phone number")
    private String shippingPhone;

    @NotBlank(message = "Delivery address is required")
    @Size(max = 500, message = "Address is too long")
    private String shippingAddress;

    @NotBlank(message = "City is required")
    @Size(max = 100, message = "City is too long")
    private String shippingCity;

    @Size(max = 20, message = "Postal code is too long")
    private String shippingPostalCode;

    @NotNull(message = "Choose a payment method")
    private PaymentMethod paymentMethod;
}

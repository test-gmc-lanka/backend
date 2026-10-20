package com.gmc.backend.model;

public enum PaymentMethod {
    CARD, // paid online through Stripe before the order is processed
    CASH_ON_DELIVERY // paid to the courier; marked paid when the order is delivered
}

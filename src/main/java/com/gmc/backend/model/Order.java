package com.gmc.backend.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Long orderId;

    @NotNull(message = "Order date is required")
    @Column(name = "order_date", nullable = false)
    private LocalDateTime orderDate;

    @NotNull(message = "Order status is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "order_type", nullable = false)
    private OrderType orderType = OrderType.B2C;

    @Column(precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(length = 1000)
    private String notes;

    // ── Price breakdown (totalAmount = subtotal + shippingFee + taxAmount) ──
    @Column(precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "shipping_fee", precision = 12, scale = 2)
    private BigDecimal shippingFee;

    @Column(name = "tax_amount", precision = 12, scale = 2)
    private BigDecimal taxAmount;

    // ── Delivery details captured at checkout ──
    @Column(name = "shipping_name")
    private String shippingName;

    @Column(name = "shipping_phone", length = 30)
    private String shippingPhone;

    @Column(name = "shipping_address", length = 500)
    private String shippingAddress;

    @Column(name = "shipping_city", length = 100)
    private String shippingCity;

    @Column(name = "shipping_postal_code", length = 20)
    private String shippingPostalCode;

    // ── Payment ──
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 30)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", length = 20)
    private PaymentStatus paymentStatus;

    @Column(name = "stripe_payment_intent_id", unique = true)
    private String stripePaymentIntentId;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @NotNull(message = "User is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();
}

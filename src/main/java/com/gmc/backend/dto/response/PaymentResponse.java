package com.gmc.backend.dto.response;

import com.gmc.backend.model.PaymentStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class PaymentResponse {

    private Long paymentId;
    private Long invoiceId;
    private BigDecimal amount;
    private String method;
    private PaymentStatus status;
    private String stripeClientSecret;
    private LocalDateTime createdAt;
}

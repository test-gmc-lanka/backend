package com.gmc.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InvoiceResponse {

    private Long invoiceId;
    private Long orderId;
    private BigDecimal amount;
    private LocalDate invoiceDate;
    private LocalDate dueDate;
    private boolean paid;
}

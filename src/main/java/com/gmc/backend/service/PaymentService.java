package com.gmc.backend.service;

import com.gmc.backend.dto.request.PaymentRequest;
import com.gmc.backend.dto.response.PaymentResponse;

public interface PaymentService {

    PaymentResponse initiatePayment(Long userId, PaymentRequest request);

    PaymentResponse getPaymentByInvoice(Long invoiceId);

    void confirmPayment(String stripePaymentIntentId);
}

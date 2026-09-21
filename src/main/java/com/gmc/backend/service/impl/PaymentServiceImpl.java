package com.gmc.backend.service.impl;

import com.gmc.backend.dto.request.PaymentRequest;
import com.gmc.backend.dto.response.PaymentResponse;
import com.gmc.backend.exception.BusinessRuleException;
import com.gmc.backend.exception.ResourceNotFoundException;
import com.gmc.backend.model.Invoice;
import com.gmc.backend.model.Payment;
import com.gmc.backend.model.PaymentStatus;
import com.gmc.backend.repository.InvoiceRepository;
import com.gmc.backend.repository.PaymentRepository;
import com.gmc.backend.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;

    @Override
    @Transactional
    public PaymentResponse initiatePayment(Long userId, PaymentRequest request) {
        Invoice invoice = invoiceRepository.findById(request.getInvoiceId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Invoice not found: " + request.getInvoiceId()));
        if (invoice.getPayment() != null
                && invoice.getPayment().getStatus() == PaymentStatus.COMPLETED) {
            throw new BusinessRuleException("Invoice is already paid");
        }
        Payment payment = Payment.builder()
                .invoice(invoice)
                .amount(invoice.getAmount())
                .method(request.getMethod())
                .status(PaymentStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
        Payment saved = paymentRepository.save(payment);
        return toResponse(saved, null);
    }

    @Override
    public PaymentResponse getPaymentByInvoice(Long invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + invoiceId));
        if (invoice.getPayment() == null) {
            throw new ResourceNotFoundException("No payment found for invoice: " + invoiceId);
        }
        return toResponse(invoice.getPayment(), null);
    }

    @Override
    @Transactional
    public void confirmPayment(String stripePaymentIntentId) {
        paymentRepository.findByStripePaymentIntentId(stripePaymentIntentId).ifPresent(payment -> {
            payment.setStatus(PaymentStatus.COMPLETED);
            paymentRepository.save(payment);
        });
    }

    private PaymentResponse toResponse(Payment payment, String clientSecret) {
        return PaymentResponse.builder()
                .paymentId(payment.getPaymentId())
                .invoiceId(payment.getInvoice().getInvoiceId())
                .amount(payment.getAmount())
                .method(payment.getMethod())
                .status(payment.getStatus())
                .stripeClientSecret(clientSecret)
                .createdAt(payment.getCreatedAt())
                .build();
    }
}

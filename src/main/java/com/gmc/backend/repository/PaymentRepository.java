package com.gmc.backend.repository;

import com.gmc.backend.model.Invoice;
import com.gmc.backend.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByInvoice(Invoice invoice);

    Optional<Payment> findByInvoice_InvoiceId(Long invoiceId);

    List<Payment> findByMethod(String method);

    Optional<Payment> findByStripePaymentIntentId(String stripePaymentIntentId);
}

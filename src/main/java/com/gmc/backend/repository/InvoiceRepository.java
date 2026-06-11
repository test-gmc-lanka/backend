package com.gmc.backend.repository;

import com.gmc.backend.model.Invoice;
import com.gmc.backend.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Optional<Invoice> findByOrder(Order order);

    Optional<Invoice> findByOrder_OrderId(Long orderId);

    List<Invoice> findByDueDateBefore(LocalDate date);

    List<Invoice> findByInvoiceDate(LocalDate invoiceDate);
}

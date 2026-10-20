package com.gmc.backend.service.impl;

import com.gmc.backend.dto.response.InvoiceResponse;
import com.gmc.backend.exception.BusinessRuleException;
import com.gmc.backend.exception.ResourceNotFoundException;
import com.gmc.backend.model.Invoice;
import com.gmc.backend.model.Order;
import com.gmc.backend.model.OrderStatus;
import com.gmc.backend.model.PaymentStatus;
import com.gmc.backend.repository.InvoiceRepository;
import com.gmc.backend.repository.OrderRepository;
import com.gmc.backend.service.InvoiceService;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InvoiceServiceImpl implements InvoiceService {

    private static final int PAYMENT_DUE_DAYS = 14;

    private final InvoiceRepository invoiceRepository;
    private final OrderRepository orderRepository;

    @Override
    @Transactional
    public InvoiceResponse generateInvoice(Long orderId) {
        Order order = orderRepository
                .findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        if (invoiceRepository.findByOrder_OrderId(orderId).isPresent()) {
            throw new BusinessRuleException("Invoice already exists for order: " + orderId);
        }
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new BusinessRuleException("Cancelled orders don't get an invoice");
        }
        if (order.getTotalAmount() == null) {
            throw new BusinessRuleException("Order total amount is not calculated");
        }
        Invoice invoice = new Invoice();
        invoice.setOrder(order);
        invoice.setAmount(order.getTotalAmount());
        invoice.setInvoiceDate(LocalDate.now());
        // Card orders are paid up front; only cash-on-delivery orders have money still due
        boolean prepaid = order.getPaymentStatus() == PaymentStatus.COMPLETED;
        invoice.setDueDate(prepaid ? LocalDate.now() : LocalDate.now().plusDays(PAYMENT_DUE_DAYS));
        return toResponse(invoiceRepository.save(invoice));
    }

    @Override
    public InvoiceResponse getInvoiceById(Long invoiceId) {
        return toResponse(invoiceRepository
                .findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + invoiceId)));
    }

    @Override
    public InvoiceResponse getInvoiceByOrder(Long orderId) {
        return toResponse(invoiceRepository
                .findByOrder_OrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found for order: " + orderId)));
    }

    @Override
    public List<InvoiceResponse> getAllInvoices() {
        return invoiceRepository.findAll().stream().map(this::toResponse).collect(Collectors.toList());
    }

    private InvoiceResponse toResponse(Invoice invoice) {
        // Payments are recorded on the order (Stripe card payment or cash collected on delivery)
        boolean paid = invoice.getOrder().getPaymentStatus() == PaymentStatus.COMPLETED;
        return InvoiceResponse.builder()
                .invoiceId(invoice.getInvoiceId())
                .orderId(invoice.getOrder().getOrderId())
                .amount(invoice.getAmount())
                .invoiceDate(invoice.getInvoiceDate())
                .dueDate(invoice.getDueDate())
                .paid(paid)
                .build();
    }
}

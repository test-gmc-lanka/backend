package com.gmc.backend.service;

import com.gmc.backend.dto.response.InvoiceResponse;

import java.util.List;

public interface InvoiceService {

    InvoiceResponse generateInvoice(Long orderId);

    InvoiceResponse getInvoiceById(Long invoiceId);

    InvoiceResponse getInvoiceByOrder(Long orderId);

    List<InvoiceResponse> getAllInvoices();
}

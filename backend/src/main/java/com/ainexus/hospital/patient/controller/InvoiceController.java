package com.ainexus.hospital.patient.controller;

import com.ainexus.hospital.patient.dto.request.CreateInvoiceRequest;
import com.ainexus.hospital.patient.dto.request.InvoiceStatusUpdateRequest;
import com.ainexus.hospital.patient.dto.request.RecordPaymentRequest;
import com.ainexus.hospital.patient.dto.response.InvoiceDetailResponse;
import com.ainexus.hospital.patient.dto.response.PagedInvoiceSummaryResponse;
import com.ainexus.hospital.patient.entity.InvoiceStatus;
import com.ainexus.hospital.patient.service.InvoiceService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    // US1 — Generate Invoice
    @PostMapping
    public ResponseEntity<InvoiceDetailResponse> createInvoice(
            @Valid @RequestBody CreateInvoiceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(invoiceService.createInvoice(request));
    }

    // US2 — List / search invoices
    @GetMapping
    public ResponseEntity<PagedInvoiceSummaryResponse> listInvoices(
            @RequestParam(required = false) String patientId,
            @RequestParam(required = false) String appointmentId,
            @RequestParam(required = false) InvoiceStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(invoiceService.listInvoices(
                patientId, appointmentId, status, dateFrom, dateTo, page, size));
    }

    // US2 — Get full invoice detail
    @GetMapping("/{invoiceId}")
    public ResponseEntity<InvoiceDetailResponse> getInvoice(@PathVariable String invoiceId) {
        return ResponseEntity.ok(invoiceService.getInvoice(invoiceId));
    }

    // US3 — Record payment
    @PostMapping("/{invoiceId}/payments")
    public ResponseEntity<InvoiceDetailResponse> recordPayment(
            @PathVariable String invoiceId,
            @Valid @RequestBody RecordPaymentRequest request) {
        return ResponseEntity.ok(invoiceService.recordPayment(invoiceId, request));
    }

    // US4 — Cancel / Write-off
    @PatchMapping("/{invoiceId}/status")
    public ResponseEntity<InvoiceDetailResponse> updateInvoiceStatus(
            @PathVariable String invoiceId,
            @Valid @RequestBody InvoiceStatusUpdateRequest request) {
        return ResponseEntity.ok(invoiceService.updateInvoiceStatus(invoiceId, request));
    }
}

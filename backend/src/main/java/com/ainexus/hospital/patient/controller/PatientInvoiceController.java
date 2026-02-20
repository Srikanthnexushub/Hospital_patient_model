package com.ainexus.hospital.patient.controller;

import com.ainexus.hospital.patient.dto.response.PagedInvoiceSummaryResponse;
import com.ainexus.hospital.patient.service.InvoiceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/patients/{patientId}/invoices")
public class PatientInvoiceController {

    private final InvoiceService invoiceService;

    public PatientInvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    // US2 — All invoices for a specific patient
    @GetMapping
    public ResponseEntity<PagedInvoiceSummaryResponse> listPatientInvoices(
            @PathVariable String patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(invoiceService.listInvoicesForPatient(patientId, page, size));
    }
}

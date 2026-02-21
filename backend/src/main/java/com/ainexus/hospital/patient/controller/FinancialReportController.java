package com.ainexus.hospital.patient.controller;

import com.ainexus.hospital.patient.dto.response.FinancialReportResponse;
import com.ainexus.hospital.patient.service.InvoiceService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/reports")
public class FinancialReportController {

    private final InvoiceService invoiceService;

    public FinancialReportController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    // US5 — Financial summary report
    @GetMapping("/financial")
    public ResponseEntity<FinancialReportResponse> getFinancialReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return ResponseEntity.ok(invoiceService.getFinancialReport(dateFrom, dateTo));
    }
}

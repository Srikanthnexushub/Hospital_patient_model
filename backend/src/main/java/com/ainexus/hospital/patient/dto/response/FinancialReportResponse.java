package com.ainexus.hospital.patient.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public record FinancialReportResponse(
        LocalDate dateFrom,
        LocalDate dateTo,
        BigDecimal totalInvoiced,
        BigDecimal totalCollected,
        BigDecimal totalOutstanding,
        BigDecimal totalWrittenOff,
        BigDecimal totalCancelled,
        int invoiceCount,
        int paidCount,
        int partialCount,
        int overdueCount,
        Map<String, BigDecimal> byPaymentMethod
) {}

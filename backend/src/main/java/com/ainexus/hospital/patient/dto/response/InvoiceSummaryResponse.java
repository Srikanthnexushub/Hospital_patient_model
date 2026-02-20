package com.ainexus.hospital.patient.dto.response;

import com.ainexus.hospital.patient.entity.InvoiceStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record InvoiceSummaryResponse(
        String invoiceId,
        String appointmentId,
        String patientId,
        String patientName,
        String doctorId,
        InvoiceStatus status,
        BigDecimal totalAmount,
        BigDecimal amountDue,
        BigDecimal amountPaid,
        OffsetDateTime createdAt
) {}

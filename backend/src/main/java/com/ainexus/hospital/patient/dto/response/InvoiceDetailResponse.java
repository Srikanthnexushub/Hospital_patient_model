package com.ainexus.hospital.patient.dto.response;

import com.ainexus.hospital.patient.entity.InvoiceStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record InvoiceDetailResponse(
        String invoiceId,
        String appointmentId,
        String appointmentDate,
        String patientId,
        String patientName,
        String doctorId,
        String doctorName,
        InvoiceStatus status,
        BigDecimal totalAmount,
        BigDecimal discountPercent,
        BigDecimal discountAmount,
        BigDecimal netAmount,
        BigDecimal taxRate,
        BigDecimal taxAmount,
        BigDecimal amountDue,
        BigDecimal amountPaid,
        String notes,
        String cancelReason,
        Integer version,
        OffsetDateTime createdAt,
        String createdBy,
        OffsetDateTime updatedAt,
        String updatedBy,
        List<LineItemResponse> lineItems,
        List<PaymentResponse> payments
) {}

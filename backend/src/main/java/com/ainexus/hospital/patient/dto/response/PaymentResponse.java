package com.ainexus.hospital.patient.dto.response;

import com.ainexus.hospital.patient.entity.PaymentMethod;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PaymentResponse(
        Long id,
        BigDecimal amount,
        PaymentMethod paymentMethod,
        String referenceNumber,
        String notes,
        OffsetDateTime paidAt,
        String recordedBy
) {}

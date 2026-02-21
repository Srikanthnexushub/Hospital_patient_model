package com.ainexus.hospital.patient.dto;

import com.ainexus.hospital.patient.entity.LabResultInterpretation;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record LabResultResponse(
        UUID id,
        UUID orderId,
        String patientId,
        String testName,
        String value,
        String unit,
        BigDecimal referenceRangeLow,
        BigDecimal referenceRangeHigh,
        LabResultInterpretation interpretation,
        String resultNotes,
        String resultedBy,
        OffsetDateTime resultedAt,
        boolean alertCreated,
        UUID alertId
) {}

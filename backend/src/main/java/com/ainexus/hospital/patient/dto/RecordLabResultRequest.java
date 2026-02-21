package com.ainexus.hospital.patient.dto;

import com.ainexus.hospital.patient.entity.LabResultInterpretation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record RecordLabResultRequest(
        @NotBlank(message = "Result value is required")
        String value,

        String unit,

        BigDecimal referenceRangeLow,

        BigDecimal referenceRangeHigh,

        @NotNull(message = "Interpretation is required")
        LabResultInterpretation interpretation,

        String resultNotes
) {}

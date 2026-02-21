package com.ainexus.hospital.patient.dto.request;

import com.ainexus.hospital.patient.entity.ProblemSeverity;
import com.ainexus.hospital.patient.entity.ProblemStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateProblemRequest(
        @NotBlank String title,
        String description,
        String icdCode,
        @NotNull ProblemSeverity severity,
        @NotNull ProblemStatus status,
        LocalDate onsetDate,
        String notes
) {
}

package com.ainexus.hospital.patient.dto.response;

import com.ainexus.hospital.patient.entity.ProblemSeverity;
import com.ainexus.hospital.patient.entity.ProblemStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ProblemResponse(
        UUID id,
        String patientId,
        String title,
        String description,
        String icdCode,
        ProblemSeverity severity,
        ProblemStatus status,
        LocalDate onsetDate,
        String notes,
        String createdBy,
        OffsetDateTime createdAt,
        String updatedBy,
        OffsetDateTime updatedAt
) {
}

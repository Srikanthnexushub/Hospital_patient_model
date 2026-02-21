package com.ainexus.hospital.patient.dto.request;

import com.ainexus.hospital.patient.entity.ProblemSeverity;
import com.ainexus.hospital.patient.entity.ProblemStatus;

import java.time.LocalDate;

/** All fields nullable — only provided (non-null) fields are updated. */
public record UpdateProblemRequest(
        String title,
        String description,
        String icdCode,
        ProblemSeverity severity,
        ProblemStatus status,
        LocalDate onsetDate,
        String notes
) {
}

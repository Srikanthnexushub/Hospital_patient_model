package com.ainexus.hospital.patient.dto.response;

import com.ainexus.hospital.patient.entity.AllergySeverity;
import com.ainexus.hospital.patient.entity.AllergyType;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AllergyResponse(
        UUID id,
        String patientId,
        String substance,
        AllergyType type,
        AllergySeverity severity,
        String reaction,
        LocalDate onsetDate,
        String notes,
        Boolean active,
        String createdBy,
        OffsetDateTime createdAt
) {
}

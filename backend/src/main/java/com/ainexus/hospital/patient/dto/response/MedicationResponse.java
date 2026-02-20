package com.ainexus.hospital.patient.dto.response;

import com.ainexus.hospital.patient.entity.MedicationRoute;
import com.ainexus.hospital.patient.entity.MedicationStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record MedicationResponse(
        UUID id,
        String patientId,
        String medicationName,
        String genericName,
        String dosage,
        String frequency,
        MedicationRoute route,
        LocalDate startDate,
        LocalDate endDate,
        String indication,
        String prescribedBy,
        MedicationStatus status,
        String notes,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}

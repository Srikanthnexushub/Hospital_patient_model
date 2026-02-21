package com.ainexus.hospital.patient.dto.request;

import com.ainexus.hospital.patient.entity.MedicationRoute;
import com.ainexus.hospital.patient.entity.MedicationStatus;

import java.time.LocalDate;

public record UpdateMedicationRequest(
        String medicationName,
        String genericName,
        String dosage,
        String frequency,
        MedicationRoute route,
        LocalDate endDate,
        String indication,
        MedicationStatus status,
        String notes
) {
}

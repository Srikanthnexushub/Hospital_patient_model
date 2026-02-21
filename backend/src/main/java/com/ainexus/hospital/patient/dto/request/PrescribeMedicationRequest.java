package com.ainexus.hospital.patient.dto.request;

import com.ainexus.hospital.patient.entity.MedicationRoute;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record PrescribeMedicationRequest(
        @NotBlank String medicationName,
        String genericName,
        @NotBlank String dosage,
        @NotBlank String frequency,
        @NotNull MedicationRoute route,
        @NotNull LocalDate startDate,
        LocalDate endDate,
        String indication,
        String notes
) {
}

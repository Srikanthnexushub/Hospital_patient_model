package com.ainexus.hospital.patient.dto.request;

import com.ainexus.hospital.patient.entity.AllergySeverity;
import com.ainexus.hospital.patient.entity.AllergyType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record RecordAllergyRequest(
        @NotBlank String substance,
        @NotNull AllergyType type,
        @NotNull AllergySeverity severity,
        @NotBlank String reaction,
        LocalDate onsetDate,
        String notes
) {
}

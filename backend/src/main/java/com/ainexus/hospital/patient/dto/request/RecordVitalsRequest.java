package com.ainexus.hospital.patient.dto.request;

import com.ainexus.hospital.patient.validation.AtLeastOneVitalPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;

/**
 * Request to record or replace vitals for an appointment.
 * All fields are optional but at least one must be non-null (enforced by @AtLeastOneVitalPresent).
 * Blood pressure diastolic must not exceed systolic when both are provided (validated in service).
 */
@AtLeastOneVitalPresent
public record RecordVitalsRequest(
        Integer bloodPressureSystolic,
        Integer bloodPressureDiastolic,
        Integer heartRate,
        BigDecimal temperature,
        BigDecimal weight,
        BigDecimal height,
        @Min(0) @Max(100) Integer oxygenSaturation,
        Integer respiratoryRate
) implements AtLeastOneVitalPresent.VitalsPayload {
}

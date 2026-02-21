package com.ainexus.hospital.patient.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record VitalsResponse(
        Long id,
        String appointmentId,
        String patientId,
        Integer bloodPressureSystolic,
        Integer bloodPressureDiastolic,
        Integer heartRate,
        BigDecimal temperature,
        BigDecimal weight,
        BigDecimal height,
        Integer oxygenSaturation,
        Integer respiratoryRate,
        String recordedBy,
        OffsetDateTime recordedAt
) {
}

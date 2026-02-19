package com.ainexus.hospital.patient.dto.response;

import com.ainexus.hospital.patient.entity.PatientStatus;

public record PatientStatusChangeResponse(
        String patientId,
        PatientStatus status,
        String message
) {}

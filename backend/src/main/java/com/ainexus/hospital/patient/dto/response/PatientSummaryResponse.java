package com.ainexus.hospital.patient.dto.response;

import com.ainexus.hospital.patient.entity.Gender;
import com.ainexus.hospital.patient.entity.PatientStatus;

public record PatientSummaryResponse(
        String patientId,
        String firstName,
        String lastName,
        int age,           // computed from dateOfBirth at read time
        Gender gender,
        String phone,
        PatientStatus status
) {}

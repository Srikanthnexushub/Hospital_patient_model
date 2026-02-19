package com.ainexus.hospital.patient.dto.response;

import com.ainexus.hospital.patient.entity.BloodGroup;
import com.ainexus.hospital.patient.entity.Gender;
import com.ainexus.hospital.patient.entity.PatientStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record PatientResponse(
        String patientId,
        String firstName,
        String lastName,
        LocalDate dateOfBirth,
        int age,           // computed at read time — never stored
        Gender gender,
        BloodGroup bloodGroup,
        String phone,
        String email,
        String address,
        String city,
        String state,
        String zipCode,
        String emergencyContactName,
        String emergencyContactPhone,
        String emergencyContactRelationship,
        String knownAllergies,
        String chronicConditions,
        PatientStatus status,
        OffsetDateTime createdAt,
        String createdBy,
        OffsetDateTime updatedAt,
        String updatedBy,
        Integer version    // included for optimistic locking in update (If-Match header)
) {}

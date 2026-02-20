package com.ainexus.hospital.patient.dto.response;

import java.time.OffsetDateTime;

public record ClinicalNotesResponse(
        String appointmentId,
        String chiefComplaint,
        String diagnosis,
        String treatment,
        String prescription,
        boolean followUpRequired,
        Integer followUpDays,
        String privateNotes,
        String createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}

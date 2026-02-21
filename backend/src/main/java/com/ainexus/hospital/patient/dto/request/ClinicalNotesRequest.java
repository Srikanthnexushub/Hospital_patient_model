package com.ainexus.hospital.patient.dto.request;

public record ClinicalNotesRequest(
        String chiefComplaint,
        String diagnosis,
        String treatment,
        String prescription,
        Boolean followUpRequired,
        Integer followUpDays,
        String privateNotes
) {}

package com.ainexus.hospital.patient.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record InteractionSummaryResponse(
        String patientId,
        List<DrugInteractionDto> interactions,
        List<String> allergyContraindications,
        boolean safe,
        OffsetDateTime checkedAt
) {}

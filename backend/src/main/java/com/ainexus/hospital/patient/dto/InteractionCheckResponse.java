package com.ainexus.hospital.patient.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record InteractionCheckResponse(
        String drugName,
        List<DrugInteractionDto> interactions,
        List<String> allergyContraindications,
        boolean safe,
        OffsetDateTime checkedAt
) {}

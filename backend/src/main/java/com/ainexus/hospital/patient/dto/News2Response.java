package com.ainexus.hospital.patient.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * API response for GET /api/v1/patients/{patientId}/news2.
 */
public record News2Response(
        Integer totalScore,
        String riskLevel,
        String riskColour,
        String recommendation,
        List<News2ComponentScoreDto> components,
        Long basedOnVitalsId,
        OffsetDateTime computedAt,
        String message
) {}

package com.ainexus.hospital.patient.intelligence;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Computed NEWS2 Early Warning Score result.
 *
 * <p>When {@code riskLevel} is {@code "NO_DATA"}, all numeric fields are null and
 * {@code message} describes why no score could be computed.
 */
public record News2Result(
        Integer totalScore,
        String riskLevel,
        String riskColour,
        String recommendation,
        List<News2ComponentScore> components,
        Long basedOnVitalsId,
        OffsetDateTime computedAt,
        String message
) {}

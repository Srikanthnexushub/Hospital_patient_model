package com.ainexus.hospital.patient.intelligence;

/**
 * Scored contribution of a single NEWS2 vital parameter.
 *
 * @param parameter  parameter name (e.g. "RESPIRATORY_RATE")
 * @param value      observed value as a string, or null if the vitals field was absent
 * @param score      NEWS2 points awarded (0–3)
 * @param unit       measurement unit (e.g. "breaths/min"), or null for unitless params
 * @param defaulted  true when the vital field was null and a safe default (score=0) was assumed
 */
public record News2ComponentScore(
        String parameter,
        String value,
        int score,
        String unit,
        boolean defaulted
) {}

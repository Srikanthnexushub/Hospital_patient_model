package com.ainexus.hospital.patient.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * One row in the risk-ranked patient dashboard.
 */
public record PatientRiskRow(
        String patientId,
        String patientName,
        String bloodGroup,
        Integer news2Score,
        String news2RiskLevel,
        String news2RiskColour,
        long criticalAlertCount,
        long warningAlertCount,
        long activeMedicationCount,
        long activeProblemCount,
        long activeAllergyCount,
        OffsetDateTime lastVitalsAt,
        LocalDate lastVisitDate
) {}

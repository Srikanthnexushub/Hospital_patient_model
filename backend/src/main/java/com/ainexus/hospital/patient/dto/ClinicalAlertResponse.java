package com.ainexus.hospital.patient.dto;

import com.ainexus.hospital.patient.entity.AlertSeverity;
import com.ainexus.hospital.patient.entity.AlertStatus;
import com.ainexus.hospital.patient.entity.AlertType;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Read-only view of a ClinicalAlert returned to API consumers.
 * patientName is populated by the service layer (not the mapper) via Patient lookup.
 */
public record ClinicalAlertResponse(
        UUID id,
        String patientId,
        String patientName,
        AlertType alertType,
        AlertSeverity severity,
        String title,
        String description,
        String source,
        String triggerValue,
        AlertStatus status,
        OffsetDateTime createdAt,
        String acknowledgedBy,
        OffsetDateTime acknowledgedAt,
        String dismissReason,
        OffsetDateTime dismissedAt
) {}

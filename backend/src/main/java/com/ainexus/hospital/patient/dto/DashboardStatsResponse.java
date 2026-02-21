package com.ainexus.hospital.patient.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * System-wide clinical stats snapshot for GET /api/v1/dashboard/stats.
 */
public record DashboardStatsResponse(
        long totalActivePatients,
        long patientsWithCriticalAlerts,
        long patientsWithHighNews2,
        long totalActiveAlerts,
        long totalCriticalAlerts,
        long totalWarningAlerts,
        List<AlertTypeCountDto> alertsByType,
        OffsetDateTime generatedAt
) {}

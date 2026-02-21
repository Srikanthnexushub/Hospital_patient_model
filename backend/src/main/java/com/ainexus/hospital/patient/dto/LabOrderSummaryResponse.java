package com.ainexus.hospital.patient.dto;

import com.ainexus.hospital.patient.entity.LabOrderCategory;
import com.ainexus.hospital.patient.entity.LabOrderPriority;
import com.ainexus.hospital.patient.entity.LabOrderStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record LabOrderSummaryResponse(
        UUID id,
        String patientId,
        String testName,
        LabOrderCategory category,
        LabOrderPriority priority,
        LabOrderStatus status,
        String orderedBy,
        OffsetDateTime orderedAt,
        boolean hasResult
) {}

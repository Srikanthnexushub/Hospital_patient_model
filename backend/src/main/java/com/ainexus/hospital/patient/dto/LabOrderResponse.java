package com.ainexus.hospital.patient.dto;

import com.ainexus.hospital.patient.entity.LabOrderCategory;
import com.ainexus.hospital.patient.entity.LabOrderPriority;
import com.ainexus.hospital.patient.entity.LabOrderStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record LabOrderResponse(
        UUID id,
        String patientId,
        String testName,
        String testCode,
        LabOrderCategory category,
        LabOrderPriority priority,
        LabOrderStatus status,
        String orderedBy,
        OffsetDateTime orderedAt,
        String appointmentId,
        String notes,
        String cancelledReason
) {}

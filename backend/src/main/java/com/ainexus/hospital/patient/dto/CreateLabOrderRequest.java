package com.ainexus.hospital.patient.dto;

import com.ainexus.hospital.patient.entity.LabOrderCategory;
import com.ainexus.hospital.patient.entity.LabOrderPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateLabOrderRequest(
        @NotBlank(message = "Test name is required")
        @Size(max = 200, message = "Test name must not exceed 200 characters")
        String testName,

        @Size(max = 50, message = "Test code must not exceed 50 characters")
        String testCode,

        @NotNull(message = "Category is required")
        LabOrderCategory category,

        /** Optional link to the triggering appointment. */
        String appointmentId,

        /** Defaults to ROUTINE if not provided. */
        LabOrderPriority priority,

        String notes
) {}

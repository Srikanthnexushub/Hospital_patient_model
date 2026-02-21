package com.ainexus.hospital.patient.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DismissAlertRequest(
        @NotBlank(message = "Dismiss reason is required")
        @Size(max = 1000, message = "Reason must not exceed 1000 characters")
        String reason
) {}

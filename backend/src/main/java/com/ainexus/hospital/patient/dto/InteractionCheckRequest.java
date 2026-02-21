package com.ainexus.hospital.patient.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InteractionCheckRequest(
        @NotBlank(message = "Drug name is required")
        @Size(max = 200, message = "Drug name must not exceed 200 characters")
        String drugName
) {}

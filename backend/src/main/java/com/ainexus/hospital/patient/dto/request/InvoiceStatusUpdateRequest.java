package com.ainexus.hospital.patient.dto.request;

import com.ainexus.hospital.patient.entity.InvoiceAction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record InvoiceStatusUpdateRequest(
        @NotNull(message = "Action is required")
        InvoiceAction action,

        @NotBlank(message = "Reason is required")
        String reason
) {}

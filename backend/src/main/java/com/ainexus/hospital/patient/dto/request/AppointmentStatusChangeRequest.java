package com.ainexus.hospital.patient.dto.request;

import com.ainexus.hospital.patient.entity.AppointmentAction;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AppointmentStatusChangeRequest(

        @NotNull(message = "action is required")
        AppointmentAction action,

        @Size(max = 500, message = "reason must not exceed 500 characters")
        String reason
) {}

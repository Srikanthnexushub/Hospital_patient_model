package com.ainexus.hospital.patient.dto.request;

import jakarta.validation.constraints.NotNull;

public record PatientStatusChangeRequest(
        @NotNull(message = "Action is required.")
        StatusAction action
) {
    public enum StatusAction {
        ACTIVATE,
        DEACTIVATE
    }
}

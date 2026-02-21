package com.ainexus.hospital.patient.entity;

public enum InteractionSeverity {
    MINOR,
    MODERATE,
    MAJOR,
    CONTRAINDICATED;

    /** MAJOR and CONTRAINDICATED interactions trigger a CRITICAL ClinicalAlert. */
    public boolean triggersAlert() {
        return this == MAJOR || this == CONTRAINDICATED;
    }
}

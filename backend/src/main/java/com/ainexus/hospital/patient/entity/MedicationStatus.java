package com.ainexus.hospital.patient.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum MedicationStatus {
    ACTIVE("ACTIVE"),
    DISCONTINUED("DISCONTINUED"),
    COMPLETED("COMPLETED");

    private final String displayValue;

    MedicationStatus(String displayValue) {
        this.displayValue = displayValue;
    }

    @JsonValue
    public String getDisplayValue() {
        return displayValue;
    }

    @JsonCreator
    public static MedicationStatus fromValue(String value) {
        if (value == null || value.isBlank()) return null;
        for (MedicationStatus s : values()) {
            if (s.displayValue.equalsIgnoreCase(value)) return s;
        }
        return valueOf(value.toUpperCase());
    }
}

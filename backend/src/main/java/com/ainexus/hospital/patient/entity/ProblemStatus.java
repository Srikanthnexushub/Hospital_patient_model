package com.ainexus.hospital.patient.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ProblemStatus {
    ACTIVE("ACTIVE"),
    RESOLVED("RESOLVED"),
    INACTIVE("INACTIVE");

    private final String displayValue;

    ProblemStatus(String displayValue) {
        this.displayValue = displayValue;
    }

    @JsonValue
    public String getDisplayValue() {
        return displayValue;
    }

    @JsonCreator
    public static ProblemStatus fromValue(String value) {
        if (value == null || value.isBlank()) return null;
        for (ProblemStatus s : values()) {
            if (s.displayValue.equalsIgnoreCase(value)) return s;
        }
        return valueOf(value.toUpperCase());
    }
}

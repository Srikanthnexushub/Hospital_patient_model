package com.ainexus.hospital.patient.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ProblemSeverity {
    MILD("MILD"),
    MODERATE("MODERATE"),
    SEVERE("SEVERE");

    private final String displayValue;

    ProblemSeverity(String displayValue) {
        this.displayValue = displayValue;
    }

    @JsonValue
    public String getDisplayValue() {
        return displayValue;
    }

    @JsonCreator
    public static ProblemSeverity fromValue(String value) {
        if (value == null || value.isBlank()) return null;
        for (ProblemSeverity s : values()) {
            if (s.displayValue.equalsIgnoreCase(value)) return s;
        }
        return valueOf(value.toUpperCase());
    }
}

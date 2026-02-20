package com.ainexus.hospital.patient.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum AllergySeverity {
    MILD("MILD"),
    MODERATE("MODERATE"),
    SEVERE("SEVERE"),
    LIFE_THREATENING("LIFE_THREATENING");

    private final String displayValue;

    AllergySeverity(String displayValue) {
        this.displayValue = displayValue;
    }

    @JsonValue
    public String getDisplayValue() {
        return displayValue;
    }

    @JsonCreator
    public static AllergySeverity fromValue(String value) {
        if (value == null || value.isBlank()) return null;
        for (AllergySeverity s : values()) {
            if (s.displayValue.equalsIgnoreCase(value)) return s;
        }
        return valueOf(value.toUpperCase());
    }
}

package com.ainexus.hospital.patient.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum MedicationRoute {
    ORAL("ORAL"),
    IV("IV"),
    IM("IM"),
    TOPICAL("TOPICAL"),
    INHALED("INHALED"),
    OTHER("OTHER");

    private final String displayValue;

    MedicationRoute(String displayValue) {
        this.displayValue = displayValue;
    }

    @JsonValue
    public String getDisplayValue() {
        return displayValue;
    }

    @JsonCreator
    public static MedicationRoute fromValue(String value) {
        if (value == null || value.isBlank()) return null;
        for (MedicationRoute r : values()) {
            if (r.displayValue.equalsIgnoreCase(value)) return r;
        }
        return valueOf(value.toUpperCase());
    }
}

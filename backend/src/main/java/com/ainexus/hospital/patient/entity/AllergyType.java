package com.ainexus.hospital.patient.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum AllergyType {
    DRUG("DRUG"),
    FOOD("FOOD"),
    ENVIRONMENTAL("ENVIRONMENTAL"),
    OTHER("OTHER");

    private final String displayValue;

    AllergyType(String displayValue) {
        this.displayValue = displayValue;
    }

    @JsonValue
    public String getDisplayValue() {
        return displayValue;
    }

    @JsonCreator
    public static AllergyType fromValue(String value) {
        if (value == null || value.isBlank()) return null;
        for (AllergyType t : values()) {
            if (t.displayValue.equalsIgnoreCase(value)) return t;
        }
        return valueOf(value.toUpperCase());
    }
}

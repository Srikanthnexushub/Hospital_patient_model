package com.ainexus.hospital.patient.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum BloodGroup {
    A_POS("A+"),
    A_NEG("A-"),
    B_POS("B+"),
    B_NEG("B-"),
    AB_POS("AB+"),
    AB_NEG("AB-"),
    O_POS("O+"),
    O_NEG("O-"),
    UNKNOWN("UNKNOWN");

    private final String displayValue;

    BloodGroup(String displayValue) {
        this.displayValue = displayValue;
    }

    /** Serialises as the human-readable display value (e.g. "A+"). */
    @JsonValue
    public String getDisplayValue() {
        return displayValue;
    }

    /**
     * Deserialises from either the display value ("A+") or the enum name ("A_POS").
     * Accepts null / blank → UNKNOWN so optional fields never cause a 500.
     */
    @JsonCreator
    public static BloodGroup fromValue(String value) {
        if (value == null || value.isBlank()) return UNKNOWN;
        for (BloodGroup bg : values()) {
            if (bg.displayValue.equalsIgnoreCase(value)) return bg;
        }
        try {
            return BloodGroup.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}

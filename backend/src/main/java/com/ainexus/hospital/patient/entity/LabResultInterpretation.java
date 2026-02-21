package com.ainexus.hospital.patient.entity;

public enum LabResultInterpretation {
    NORMAL,
    LOW,
    HIGH,
    CRITICAL_LOW,
    CRITICAL_HIGH,
    ABNORMAL;

    /** Returns true for CRITICAL_LOW and CRITICAL_HIGH — triggers CRITICAL alert. */
    public boolean isCritical() {
        return this == CRITICAL_LOW || this == CRITICAL_HIGH;
    }

    /** Returns true for LOW and HIGH — triggers WARNING alert. */
    public boolean isOutOfRange() {
        return this == LOW || this == HIGH;
    }
}

package com.ainexus.hospital.patient.entity;

public enum AlertType {
    LAB_CRITICAL,
    LAB_ABNORMAL,
    NEWS2_HIGH,
    NEWS2_CRITICAL,
    DRUG_INTERACTION,
    ALLERGY_CONTRAINDICATION;

    /** NEWS2 alert types are deduplicated — only one ACTIVE per patient at a time. */
    public boolean isNews2Type() {
        return this == NEWS2_HIGH || this == NEWS2_CRITICAL;
    }
}

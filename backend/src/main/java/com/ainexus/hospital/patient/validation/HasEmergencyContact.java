package com.ainexus.hospital.patient.validation;

/**
 * Common interface for request types that carry emergency contact fields.
 * Used by EmergencyContactPairingValidator to validate both
 * PatientRegistrationRequest and PatientUpdateRequest with a single validator.
 */
public interface HasEmergencyContact {
    String emergencyContactName();
    String emergencyContactPhone();
}

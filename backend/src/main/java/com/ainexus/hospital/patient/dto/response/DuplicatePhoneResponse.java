package com.ainexus.hospital.patient.dto.response;

public record DuplicatePhoneResponse(
        boolean duplicate,
        String patientId,    // null if no duplicate
        String patientName   // "firstName lastName" — null if no duplicate
) {
    public static DuplicatePhoneResponse noDuplicate() {
        return new DuplicatePhoneResponse(false, null, null);
    }

    public static DuplicatePhoneResponse found(String patientId, String firstName, String lastName) {
        return new DuplicatePhoneResponse(true, patientId, firstName + " " + lastName);
    }
}

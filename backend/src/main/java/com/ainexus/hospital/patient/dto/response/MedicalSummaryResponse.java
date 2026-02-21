package com.ainexus.hospital.patient.dto.response;

import java.time.LocalDate;
import java.util.List;

public record MedicalSummaryResponse(
        String patientName,
        String bloodGroup,
        List<ProblemResponse> activeProblems,
        List<MedicationResponse> activeMedications,
        List<AllergyResponse> allergies,
        List<VitalsResponse> recentVitals,
        LocalDate lastVisitDate,
        LocalDate nextAppointmentDate,
        long totalVisits
) {
}

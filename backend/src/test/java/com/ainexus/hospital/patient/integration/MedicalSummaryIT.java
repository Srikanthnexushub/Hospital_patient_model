package com.ainexus.hospital.patient.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("emr")
@DisplayName("Medical Summary — US5: Complete Clinical Snapshot")
class MedicalSummaryIT extends BaseIntegrationTest {

    private String patientId;

    @BeforeEach
    void setup() {
        seedDoctorWithId("U2025001", "drsmith");
        patientId = seedPatient("David", "Wilson");
    }

    @Test
    @DisplayName("US5-S1: DOCTOR gets empty summary for patient with no clinical data")
    void getMedicalSummary_noClinicalData_returns200WithEmptyLists() {
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/medical-summary"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders("DOCTOR")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) resp.getBody().get("activeProblems")).isEmpty();
        assertThat((List<?>) resp.getBody().get("activeMedications")).isEmpty();
        assertThat((List<?>) resp.getBody().get("allergies")).isEmpty();
        assertThat((List<?>) resp.getBody().get("recentVitals")).isEmpty();
        assertThat(resp.getBody().get("totalVisits")).isEqualTo(0);
        assertThat(resp.getBody().get("lastVisitDate")).isNull();
    }

    @Test
    @DisplayName("US5-S2: DOCTOR gets summary with active problem, medication, and allergy")
    void getMedicalSummary_withClinicalData_returnsPopulatedSummary() {
        // Add a problem
        restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/problems"),
                HttpMethod.POST,
                new HttpEntity<>(Map.of("title", "Hypertension", "severity", "MODERATE", "status", "ACTIVE"),
                        authHeaders("DOCTOR")),
                Map.class);

        // Add a medication
        restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/medications"),
                HttpMethod.POST,
                new HttpEntity<>(Map.of("medicationName", "Lisinopril", "dosage", "10mg",
                        "frequency", "Daily", "route", "ORAL", "startDate", "2026-01-01"),
                        authHeaders("DOCTOR")),
                Map.class);

        // Add an allergy
        restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/allergies"),
                HttpMethod.POST,
                new HttpEntity<>(Map.of("substance", "Penicillin", "type", "DRUG",
                        "severity", "SEVERE", "reaction", "Anaphylaxis"),
                        authHeaders("DOCTOR")),
                Map.class);

        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/medical-summary"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders("DOCTOR")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) resp.getBody().get("activeProblems")).hasSize(1);
        assertThat((List<?>) resp.getBody().get("activeMedications")).hasSize(1);
        assertThat((List<?>) resp.getBody().get("allergies")).hasSize(1);
    }

    @Test
    @DisplayName("US5-S3: NURSE is denied — 403")
    void getMedicalSummary_nurseRole_returns403() {
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/medical-summary"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders("NURSE")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("US5-S4: RECEPTIONIST is denied — 403")
    void getMedicalSummary_receptionistRole_returns403() {
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/medical-summary"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders("RECEPTIONIST")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("US5-S5: Patient not found returns 404")
    void getMedicalSummary_unknownPatient_returns404() {
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/patients/P9999999/medical-summary"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders("DOCTOR")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("US5-S6: Summary includes lastVisitDate from completed appointment")
    void getMedicalSummary_withCompletedAppointment_showsLastVisitDate() {
        seedAppointment(patientId, "U2025001", LocalDate.of(2025, 12, 15));

        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/medical-summary"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders("DOCTOR")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("lastVisitDate")).isEqualTo("2025-12-15");
        assertThat((Integer) resp.getBody().get("totalVisits")).isEqualTo(1);
    }
}

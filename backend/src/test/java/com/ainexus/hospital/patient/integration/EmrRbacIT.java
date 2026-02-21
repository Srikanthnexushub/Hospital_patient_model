package com.ainexus.hospital.patient.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EMR RBAC matrix — verifies role-based access for all 13 EMR endpoints.
 *
 * Expected access:
 *
 * Endpoint                              RECEPTIONIST  NURSE  DOCTOR  ADMIN
 * POST /appointments/{id}/vitals        403           201    201     201
 * GET  /appointments/{id}/vitals        403           200    200     200
 * GET  /patients/{id}/vitals            403           200    200     200
 * POST /patients/{id}/problems          403           403    201     201
 * GET  /patients/{id}/problems          403           200    200     200
 * PATCH /patients/{id}/problems/{id}    403           403    200     200
 * POST /patients/{id}/medications       403           403    201     201
 * GET  /patients/{id}/medications       403           200    200     200
 * PATCH /patients/{id}/medications/{id} 403           403    200     200
 * POST /patients/{id}/allergies         403           201    201     201
 * GET  /patients/{id}/allergies         200           200    200     200
 * DELETE /patients/{id}/allergies/{id}  403           204    204     204
 * GET  /patients/{id}/medical-summary   403           403    200     200
 */
@Tag("emr")
@DisplayName("EMR RBAC — Role Matrix for all 13 endpoints")
class EmrRbacIT extends BaseIntegrationTest {

    private String patientId;
    private String apptId;

    @BeforeEach
    void setup() {
        seedDoctorWithId("U2025001", "drsmith");
        patientId = seedPatient("RBAC", "Test");
        apptId = bookAppointment(patientId, "U2025001",
                LocalDate.of(2026, 4, 1), LocalTime.of(10, 0));

        // Pre-record vitals so GET endpoints have data
        restTemplate.exchange(
                baseUrl("/api/v1/appointments/" + apptId + "/vitals"),
                HttpMethod.POST,
                new HttpEntity<>(Map.of("heartRate", 70), authHeaders("NURSE")),
                Map.class);
    }

    // ── POST vitals ───────────────────────────────────────────────────────────

    @Test @DisplayName("POST vitals: RECEPTIONIST → 403")
    void postVitals_receptionist_403() {
        assertStatus("/api/v1/appointments/" + apptId + "/vitals",
                HttpMethod.POST, Map.of("heartRate", 70), "RECEPTIONIST", HttpStatus.FORBIDDEN);
    }

    @Test @DisplayName("POST vitals: NURSE → 201")
    void postVitals_nurse_201() {
        assertStatus("/api/v1/appointments/" + apptId + "/vitals",
                HttpMethod.POST, Map.of("heartRate", 70), "NURSE", HttpStatus.CREATED);
    }

    @Test @DisplayName("POST vitals: DOCTOR → 201")
    void postVitals_doctor_201() {
        assertStatus("/api/v1/appointments/" + apptId + "/vitals",
                HttpMethod.POST, Map.of("heartRate", 70), "DOCTOR", HttpStatus.CREATED);
    }

    @Test @DisplayName("POST vitals: ADMIN → 201")
    void postVitals_admin_201() {
        assertStatus("/api/v1/appointments/" + apptId + "/vitals",
                HttpMethod.POST, Map.of("heartRate", 70), "ADMIN", HttpStatus.CREATED);
    }

    // ── GET vitals by appointment ─────────────────────────────────────────────

    @Test @DisplayName("GET vitals by appt: RECEPTIONIST → 403")
    void getVitalsByAppt_receptionist_403() {
        assertStatus("/api/v1/appointments/" + apptId + "/vitals",
                HttpMethod.GET, null, "RECEPTIONIST", HttpStatus.FORBIDDEN);
    }

    @Test @DisplayName("GET vitals by appt: NURSE → 200")
    void getVitalsByAppt_nurse_200() {
        assertStatus("/api/v1/appointments/" + apptId + "/vitals",
                HttpMethod.GET, null, "NURSE", HttpStatus.OK);
    }

    // ── GET allergies (RECEPTIONIST allowed) ─────────────────────────────────

    @Test @DisplayName("GET allergies: RECEPTIONIST → 200")
    void getAllergies_receptionist_200() {
        assertStatus("/api/v1/patients/" + patientId + "/allergies",
                HttpMethod.GET, null, "RECEPTIONIST", HttpStatus.OK);
    }

    // ── GET medical-summary (NURSE/RECEPTIONIST denied) ──────────────────────

    @Test @DisplayName("GET medical-summary: NURSE → 403")
    void getMedicalSummary_nurse_403() {
        assertStatus("/api/v1/patients/" + patientId + "/medical-summary",
                HttpMethod.GET, null, "NURSE", HttpStatus.FORBIDDEN);
    }

    @Test @DisplayName("GET medical-summary: RECEPTIONIST → 403")
    void getMedicalSummary_receptionist_403() {
        assertStatus("/api/v1/patients/" + patientId + "/medical-summary",
                HttpMethod.GET, null, "RECEPTIONIST", HttpStatus.FORBIDDEN);
    }

    @Test @DisplayName("GET medical-summary: DOCTOR → 200")
    void getMedicalSummary_doctor_200() {
        assertStatus("/api/v1/patients/" + patientId + "/medical-summary",
                HttpMethod.GET, null, "DOCTOR", HttpStatus.OK);
    }

    @Test @DisplayName("GET medical-summary: ADMIN → 200")
    void getMedicalSummary_admin_200() {
        assertStatus("/api/v1/patients/" + patientId + "/medical-summary",
                HttpMethod.GET, null, "ADMIN", HttpStatus.OK);
    }

    // ── POST problems (NURSE denied) ──────────────────────────────────────────

    @Test @DisplayName("POST problem: NURSE → 403")
    void postProblem_nurse_403() {
        assertStatus("/api/v1/patients/" + patientId + "/problems",
                HttpMethod.POST,
                Map.of("title", "Test", "severity", "MILD", "status", "ACTIVE"),
                "NURSE", HttpStatus.FORBIDDEN);
    }

    @Test @DisplayName("POST problem: DOCTOR → 201")
    void postProblem_doctor_201() {
        assertStatus("/api/v1/patients/" + patientId + "/problems",
                HttpMethod.POST,
                Map.of("title", "Test", "severity", "MILD", "status", "ACTIVE"),
                "DOCTOR", HttpStatus.CREATED);
    }

    // ── GET problems (NURSE allowed) ──────────────────────────────────────────

    @Test @DisplayName("GET problems: NURSE → 200")
    void getProblems_nurse_200() {
        assertStatus("/api/v1/patients/" + patientId + "/problems",
                HttpMethod.GET, null, "NURSE", HttpStatus.OK);
    }

    @Test @DisplayName("GET problems: RECEPTIONIST → 403")
    void getProblems_receptionist_403() {
        assertStatus("/api/v1/patients/" + patientId + "/problems",
                HttpMethod.GET, null, "RECEPTIONIST", HttpStatus.FORBIDDEN);
    }

    // ── POST medications (NURSE denied) ───────────────────────────────────────

    @Test @DisplayName("POST medication: NURSE → 403")
    void postMedication_nurse_403() {
        assertStatus("/api/v1/patients/" + patientId + "/medications",
                HttpMethod.POST,
                Map.of("medicationName", "Drug", "dosage", "5mg",
                        "frequency", "Daily", "route", "ORAL", "startDate", "2026-01-01"),
                "NURSE", HttpStatus.FORBIDDEN);
    }

    // ── Unauthenticated requests are rejected ─────────────────────────────────

    @Test @DisplayName("No token → 401 on any EMR endpoint")
    void noToken_returns401() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/problems"),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void assertStatus(String path, HttpMethod method, Object body, String role, HttpStatus expected) {
        HttpEntity<?> entity = body != null
                ? new HttpEntity<>(body, authHeaders(role))
                : new HttpEntity<>(authHeaders(role));

        // Use String.class to avoid deserialization issues when body is an array (e.g. empty list [])
        ResponseEntity<String> resp = restTemplate.exchange(
                baseUrl(path), method, entity, String.class);

        assertThat(resp.getStatusCode())
                .as("Role %s on %s %s", role, method, path)
                .isEqualTo(expected);
    }
}

package com.ainexus.hospital.patient.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("clinical-intelligence")
@DisplayName("US4 — Clinical Alerts Feed")
class ClinicalAlertIT extends BaseIntegrationTest {

    private String patientId;

    @BeforeEach
    void setupPatient() {
        patientId = seedPatient("Alert", "Patient");
    }

    // ── GET per-patient alerts ────────────────────────────────────────────────

    @Test
    @DisplayName("GET /patients/{id}/alerts — returns empty page when no alerts")
    void getPatientAlerts_empty_returns200() {
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/alerts"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders("DOCTOR")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((Integer) resp.getBody().get("totalElements")).isEqualTo(0);
    }

    @Test
    @DisplayName("GET /patients/{id}/alerts — returns seeded alert")
    void getPatientAlerts_withAlert_returnsAlert() {
        UUID alertId = seedClinicalAlert(patientId, "LAB_CRITICAL", "CRITICAL", "ACTIVE");

        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/alerts"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders("NURSE")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((Integer) resp.getBody().get("totalElements")).isEqualTo(1);
    }

    @Test
    @DisplayName("GET /patients/{id}/alerts — RECEPTIONIST gets 403")
    void getPatientAlerts_receptionist_returns403() {
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/alerts"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders("RECEPTIONIST")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── GET global alerts ────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /alerts — returns all active alerts for ADMIN")
    void getGlobalAlerts_admin_returns200() {
        seedClinicalAlert(patientId, "LAB_CRITICAL", "CRITICAL", "ACTIVE");
        seedClinicalAlert(patientId, "NEWS2_HIGH", "WARNING", "ACTIVE");

        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/alerts?status=ACTIVE"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders("ADMIN")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((Integer) resp.getBody().get("totalElements")).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("GET /alerts — RECEPTIONIST gets 403")
    void getGlobalAlerts_receptionist_returns403() {
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/alerts"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders("RECEPTIONIST")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── PATCH acknowledge ─────────────────────────────────────────────────────

    @Test
    @DisplayName("PATCH /alerts/{id}/acknowledge — status set to ACKNOWLEDGED")
    void acknowledge_doctor_setsAcknowledgedStatus() {
        UUID alertId = seedClinicalAlert(patientId, "LAB_CRITICAL", "CRITICAL", "ACTIVE");

        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/alerts/" + alertId + "/acknowledge"),
                HttpMethod.PATCH,
                new HttpEntity<>(authHeaders("DOCTOR")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("status")).isEqualTo("ACKNOWLEDGED");
        assertThat(resp.getBody().get("acknowledgedBy")).isNotNull();
        assertThat(resp.getBody().get("acknowledgedAt")).isNotNull();
    }

    @Test
    @DisplayName("PATCH acknowledge — RECEPTIONIST gets 403")
    void acknowledge_receptionist_returns403() {
        UUID alertId = seedClinicalAlert(patientId, "LAB_CRITICAL", "CRITICAL", "ACTIVE");

        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/alerts/" + alertId + "/acknowledge"),
                HttpMethod.PATCH,
                new HttpEntity<>(authHeaders("RECEPTIONIST")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── PATCH dismiss ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("PATCH /alerts/{id}/dismiss — status set to DISMISSED with reason")
    void dismiss_nurse_setsDismissedStatus() {
        UUID alertId = seedClinicalAlert(patientId, "NEWS2_HIGH", "WARNING", "ACTIVE");

        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/alerts/" + alertId + "/dismiss"),
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("reason", "Patient reviewed, vitals improving"),
                        authHeaders("NURSE")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("status")).isEqualTo("DISMISSED");
        assertThat(resp.getBody().get("dismissReason")).isEqualTo("Patient reviewed, vitals improving");
    }

    @Test
    @DisplayName("PATCH dismiss with blank reason — 400")
    void dismiss_blankReason_returns400() {
        UUID alertId = seedClinicalAlert(patientId, "LAB_CRITICAL", "CRITICAL", "ACTIVE");

        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/alerts/" + alertId + "/dismiss"),
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("reason", ""), authHeaders("DOCTOR")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── Filter by status and severity ────────────────────────────────────────

    @Test
    @DisplayName("GET /patients/{id}/alerts?status=ACKNOWLEDGED — returns only acknowledged")
    void getPatientAlerts_filterByStatus() {
        seedClinicalAlert(patientId, "LAB_CRITICAL",  "CRITICAL", "ACTIVE");
        seedClinicalAlert(patientId, "LAB_ABNORMAL",  "WARNING",  "ACKNOWLEDGED");

        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/alerts?status=ACKNOWLEDGED"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders("ADMIN")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((Integer) resp.getBody().get("totalElements")).isEqualTo(1);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private UUID seedClinicalAlert(String pid, String alertType, String severity, String status) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO clinical_alerts
                  (id, patient_id, alert_type, severity, title, description,
                   source, status, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())
                """, id, pid, alertType, severity,
                "Test alert", "Test description", "TestSource", status);
        return id;
    }
}

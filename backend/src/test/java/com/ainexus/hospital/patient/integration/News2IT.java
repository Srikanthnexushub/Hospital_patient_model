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
@DisplayName("US2 — NEWS2 Early Warning Score")
class News2IT extends BaseIntegrationTest {

    private String patientId;

    @BeforeEach
    void setupPatient() {
        patientId = seedPatient("News", "Patient");
        // Seed a doctor and appointment so that patient_vitals FK is satisfied
        seedDoctorWithId("U2025001", "drsmith");
        seedAppointment(patientId, "U2025001", java.time.LocalDate.of(2026, 1, 10));
    }

    // ── No vitals ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("No vitals on record → 200, riskLevel=NO_DATA")
    void news2_noVitals_returnsNoData() {
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/news2"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders("DOCTOR")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("riskLevel")).isEqualTo("NO_DATA");
        assertThat(resp.getBody().get("totalScore")).isNull();
    }

    // ── LOW risk ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Normal vitals → LOW risk, no clinical alert created")
    void news2_normalVitals_lowRisk_noAlert() {
        seedVitals(patientId, 16, 98, 120, 75, 37.0);

        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/news2"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders("DOCTOR")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("riskLevel")).isEqualTo("LOW");
        assertThat((Integer) resp.getBody().get("totalScore")).isEqualTo(0);

        Integer alertCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM clinical_alerts WHERE patient_id = ?",
                Integer.class, patientId);
        assertThat(alertCount).isEqualTo(0);
    }

    // ── HIGH risk (score ≥ 7) → NEWS2_CRITICAL alert ─────────────────────────

    @Test
    @DisplayName("Critically abnormal vitals → HIGH risk, NEWS2_CRITICAL alert auto-created")
    void news2_criticalVitals_highRisk_criticalAlertCreated() {
        // RR=30(3) + SpO2=89(3) + SBP=85(3) = 9 → HIGH
        seedVitals(patientId, 30, 89, 85, 75, 37.0);

        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/news2"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders("NURSE")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("riskLevel")).isEqualTo("HIGH");

        Integer alertCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM clinical_alerts WHERE patient_id = ? AND alert_type = 'NEWS2_CRITICAL' AND severity = 'CRITICAL'",
                Integer.class, patientId);
        assertThat(alertCount).isEqualTo(1);
    }

    // ── Deduplication: calling NEWS2 twice for HIGH risk → still only one active alert ──

    @Test
    @DisplayName("NEWS2 HIGH called twice → old alert auto-dismissed, one ACTIVE alert remains")
    void news2_calledTwice_deduplicatesAlert() {
        seedVitals(patientId, 30, 89, 85, 75, 37.0);

        // First call
        restTemplate.exchange(baseUrl("/api/v1/patients/" + patientId + "/news2"),
                HttpMethod.GET, new HttpEntity<>(authHeaders("DOCTOR")), Map.class);

        // Second call (same vitals → same HIGH score)
        restTemplate.exchange(baseUrl("/api/v1/patients/" + patientId + "/news2"),
                HttpMethod.GET, new HttpEntity<>(authHeaders("DOCTOR")), Map.class);

        Integer activeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM clinical_alerts WHERE patient_id = ? AND alert_type = 'NEWS2_CRITICAL' AND status = 'ACTIVE'",
                Integer.class, patientId);
        assertThat(activeCount).isEqualTo(1);
    }

    // ── Role access ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("RECEPTIONIST cannot access NEWS2 — 403")
    void news2_receptionist_returns403() {
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/news2"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders("RECEPTIONIST")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("ADMIN can access NEWS2")
    void news2_admin_returns200() {
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/news2"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders("ADMIN")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /**
     * Seeds patient_vitals for the seeded appointment (APT2025xxxx from @BeforeEach).
     * The appointment_id is derived from BaseIntegrationTest.seedAppointment hash.
     * respiratoryRate, oxygenSaturation, bloodPressureSystolic, heartRate, temperature.
     */
    private void seedVitals(String pid, int rr, int spo2, int sbp, int hr, double temp) {
        // Look up the appointment that was seeded in @BeforeEach
        String apptId = jdbcTemplate.queryForObject(
                "SELECT appointment_id FROM appointments WHERE patient_id = ? LIMIT 1",
                String.class, pid);
        jdbcTemplate.update("""
                INSERT INTO patient_vitals
                  (appointment_id, patient_id, respiratory_rate, oxygen_saturation,
                   blood_pressure_systolic, heart_rate, temperature, recorded_by, recorded_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'test', NOW())
                ON CONFLICT (appointment_id) DO UPDATE
                  SET respiratory_rate = EXCLUDED.respiratory_rate,
                      oxygen_saturation = EXCLUDED.oxygen_saturation,
                      blood_pressure_systolic = EXCLUDED.blood_pressure_systolic,
                      heart_rate = EXCLUDED.heart_rate,
                      temperature = EXCLUDED.temperature
                """,
                apptId, pid, rr, spo2, sbp, hr, temp);
    }
}

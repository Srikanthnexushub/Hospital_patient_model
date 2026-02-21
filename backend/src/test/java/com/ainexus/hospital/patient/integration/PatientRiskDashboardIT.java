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
@DisplayName("US5 — Patient Risk Dashboard")
class PatientRiskDashboardIT extends BaseIntegrationTest {

    private String patientId;

    @BeforeEach
    void setupData() {
        patientId = seedPatient("Dashboard", "Patient");
    }

    // ── GET /dashboard/patient-risk ──────────────────────────────────────────

    @Test
    @DisplayName("ADMIN gets paginated patient-risk — 200")
    void patientRisk_admin_returns200() {
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/dashboard/patient-risk?page=0&size=20"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders("ADMIN")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).containsKey("content");
        assertThat(resp.getBody()).containsKey("totalElements");
    }

    @Test
    @DisplayName("ADMIN sees seeded active patient in patient-risk")
    void patientRisk_admin_seesActivePatient() {
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/dashboard/patient-risk?page=0&size=20"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders("ADMIN")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((Integer) resp.getBody().get("totalElements")).isGreaterThanOrEqualTo(1);

        @SuppressWarnings("unchecked")
        var rows = (java.util.List<Map<String, Object>>) resp.getBody().get("content");
        assertThat(rows).anySatisfy(row -> {
            assertThat(row.get("patientId")).isEqualTo(patientId);
            assertThat(row).containsKey("news2RiskLevel");
            assertThat(row).containsKey("criticalAlertCount");
            assertThat(row).containsKey("warningAlertCount");
        });
    }

    @Test
    @DisplayName("NURSE cannot access patient-risk — 403")
    void patientRisk_nurse_returns403() {
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/dashboard/patient-risk"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders("NURSE")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("RECEPTIONIST cannot access patient-risk — 403")
    void patientRisk_receptionist_returns403() {
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/dashboard/patient-risk"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders("RECEPTIONIST")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("DOCTOR accesses patient-risk — 200 (scoped to own appointments)")
    void patientRisk_doctor_returns200() {
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/dashboard/patient-risk?page=0&size=20"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders("DOCTOR")),
                Map.class);

        // DOCTOR with no appointments should see empty list (scoped)
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((Integer) resp.getBody().get("totalElements")).isEqualTo(0);
    }

    @Test
    @DisplayName("DOCTOR sees patient after appointment booked")
    void patientRisk_doctor_seesOwnPatient() {
        seedDoctorWithId("U2025001", "drsmith");
        seedAppointment(patientId, "U2025001", java.time.LocalDate.of(2026, 1, 15));

        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/dashboard/patient-risk?page=0&size=20"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders("DOCTOR")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((Integer) resp.getBody().get("totalElements")).isEqualTo(1);
    }

    // ── Sort order: patient with critical alert comes first ───────────────────

    @Test
    @DisplayName("Patient with critical alerts appears before patient without")
    void patientRisk_sortOrder_criticalFirst() {
        String safePatient = seedPatient("Safe", "Patient");
        String criticalPatient = seedPatient("Critical", "Patient");

        // Give critical patient an active CRITICAL alert
        jdbcTemplate.update("""
                INSERT INTO clinical_alerts (id, patient_id, alert_type, severity, title,
                  description, source, status, created_at)
                VALUES (gen_random_uuid(), ?, 'LAB_CRITICAL', 'CRITICAL', 'Critical alert',
                        'desc', 'test', 'ACTIVE', NOW())
                """, criticalPatient);

        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/dashboard/patient-risk?page=0&size=20"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders("ADMIN")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        var rows = (java.util.List<Map<String, Object>>) resp.getBody().get("content");
        assertThat(rows.size()).isGreaterThanOrEqualTo(2);

        // Critical patient must appear before safe patient
        int criticalIdx = indexOfPatient(rows, criticalPatient);
        int safeIdx      = indexOfPatient(rows, safePatient);
        assertThat(criticalIdx).isLessThan(safeIdx);
    }

    // ── GET /dashboard/stats ──────────────────────────────────────────────────

    @Test
    @DisplayName("GET /dashboard/stats — returns expected fields")
    void getStats_admin_returns200WithFields() {
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/dashboard/stats"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders("ADMIN")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).containsKeys(
                "totalActivePatients", "patientsWithCriticalAlerts",
                "patientsWithHighNews2", "totalActiveAlerts",
                "totalCriticalAlerts", "totalWarningAlerts",
                "alertsByType", "generatedAt");
    }

    @Test
    @DisplayName("Stats reflect active alert counts accurately")
    void getStats_reflectsAlertCounts() {
        seedClinicalAlert(patientId, "LAB_CRITICAL", "CRITICAL", "ACTIVE");
        seedClinicalAlert(patientId, "NEWS2_HIGH", "WARNING", "ACTIVE");

        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/dashboard/stats"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders("DOCTOR")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((Integer) resp.getBody().get("totalActiveAlerts")).isGreaterThanOrEqualTo(2);
        assertThat((Integer) resp.getBody().get("totalCriticalAlerts")).isGreaterThanOrEqualTo(1);
        assertThat((Integer) resp.getBody().get("totalWarningAlerts")).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("GET /dashboard/stats — NURSE gets 403")
    void getStats_nurse_returns403() {
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/dashboard/stats"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders("NURSE")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("GET /dashboard/stats — RECEPTIONIST gets 403")
    void getStats_receptionist_returns403() {
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/dashboard/stats"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders("RECEPTIONIST")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void seedClinicalAlert(String pid, String alertType, String severity, String status) {
        jdbcTemplate.update("""
                INSERT INTO clinical_alerts
                  (id, patient_id, alert_type, severity, title, description, source, status, created_at)
                VALUES (gen_random_uuid(), ?, ?, ?, 'Test', 'Desc', 'Test', ?, NOW())
                """, pid, alertType, severity, status);
    }

    private int indexOfPatient(java.util.List<Map<String, Object>> rows, String pid) {
        for (int i = 0; i < rows.size(); i++) {
            if (pid.equals(rows.get(i).get("patientId"))) return i;
        }
        return Integer.MAX_VALUE;
    }
}

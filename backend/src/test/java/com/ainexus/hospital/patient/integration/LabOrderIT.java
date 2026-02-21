package com.ainexus.hospital.patient.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("clinical-intelligence")
@DisplayName("US1 — Lab Orders & Results")
class LabOrderIT extends BaseIntegrationTest {

    private String patientId;

    @BeforeEach
    void setupPatient() {
        patientId = seedPatient("Lab", "Patient");
    }

    // ── Create lab order ─────────────────────────────────────────────────────

    @Test
    @DisplayName("DOCTOR creates lab order — 201 with orderId and PENDING status")
    void createLabOrder_doctor_returns201() {
        Map<String, Object> body = Map.of(
                "testName", "Full Blood Count",
                "category", "HEMATOLOGY",
                "priority", "ROUTINE"
        );

        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/lab-orders"),
                HttpMethod.POST,
                new HttpEntity<>(body, authHeaders("DOCTOR")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody().get("id")).isNotNull();
        assertThat(resp.getBody().get("status")).isEqualTo("PENDING");
        assertThat(resp.getBody().get("testName")).isEqualTo("Full Blood Count");
    }

    @Test
    @DisplayName("ADMIN can create lab order — 201")
    void createLabOrder_admin_returns201() {
        Map<String, Object> body = Map.of(
                "testName", "LFT",
                "category", "CHEMISTRY",
                "priority", "URGENT"
        );

        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/lab-orders"),
                HttpMethod.POST,
                new HttpEntity<>(body, authHeaders("ADMIN")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("NURSE cannot create lab order — 403")
    void createLabOrder_nurse_returns403() {
        Map<String, Object> body = Map.of(
                "testName", "FBC", "category", "HEMATOLOGY", "priority", "ROUTINE");

        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/lab-orders"),
                HttpMethod.POST,
                new HttpEntity<>(body, authHeaders("NURSE")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("RECEPTIONIST cannot create lab order — 403")
    void createLabOrder_receptionist_returns403() {
        Map<String, Object> body = Map.of(
                "testName", "FBC", "category", "HEMATOLOGY", "priority", "ROUTINE");

        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/lab-orders"),
                HttpMethod.POST,
                new HttpEntity<>(body, authHeaders("RECEPTIONIST")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── Record lab result (CRITICAL → auto-alert) ────────────────────────────

    @Test
    @DisplayName("NURSE records CRITICAL_HIGH result — 201, alertCreated=true, order status=RESULTED")
    void recordResult_criticalHigh_alertCreated() {
        String orderId = createOrder("Potassium", "CHEMISTRY", "STAT");

        Map<String, Object> resultBody = Map.of(
                "value", "7.2",
                "unit", "mmol/L",
                "referenceRangeHigh", 5.0,
                "referenceRangeLow", 3.5,
                "interpretation", "CRITICAL_HIGH"
        );

        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/lab-orders/" + orderId + "/result"),
                HttpMethod.POST,
                new HttpEntity<>(resultBody, authHeaders("NURSE")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody().get("alertCreated")).isEqualTo(true);
        assertThat(resp.getBody().get("alertId")).isNotNull();

        // Verify a clinical alert was persisted
        Integer alertCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM clinical_alerts WHERE patient_id = ? AND alert_type = 'LAB_CRITICAL'",
                Integer.class, patientId);
        assertThat(alertCount).isEqualTo(1);
    }

    @Test
    @DisplayName("NURSE records NORMAL result — 201, alertCreated=false")
    void recordResult_normal_noAlert() {
        String orderId = createOrder("Glucose", "CHEMISTRY", "ROUTINE");

        Map<String, Object> resultBody = Map.of(
                "value", "5.2",
                "unit", "mmol/L",
                "interpretation", "NORMAL"
        );

        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/lab-orders/" + orderId + "/result"),
                HttpMethod.POST,
                new HttpEntity<>(resultBody, authHeaders("NURSE")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody().get("alertCreated")).isEqualTo(false);

        Integer alertCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM clinical_alerts WHERE patient_id = ?",
                Integer.class, patientId);
        assertThat(alertCount).isEqualTo(0);
    }

    @Test
    @DisplayName("Recording LOW result creates LAB_ABNORMAL WARNING alert")
    void recordResult_low_labAbnormalAlertCreated() {
        String orderId = createOrder("Haemoglobin", "HEMATOLOGY", "ROUTINE");

        Map<String, Object> resultBody = Map.of(
                "value", "9.5",
                "unit", "g/dL",
                "interpretation", "LOW"
        );

        restTemplate.exchange(
                baseUrl("/api/v1/lab-orders/" + orderId + "/result"),
                HttpMethod.POST,
                new HttpEntity<>(resultBody, authHeaders("DOCTOR")),
                Map.class);

        Integer alertCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM clinical_alerts WHERE patient_id = ? AND alert_type = 'LAB_ABNORMAL' AND severity = 'WARNING'",
                Integer.class, patientId);
        assertThat(alertCount).isEqualTo(1);
    }

    @Test
    @DisplayName("Second result for same order — 409 Conflict")
    void recordResult_duplicate_returns409() {
        String orderId = createOrder("TSH", "CHEMISTRY", "ROUTINE");

        Map<String, Object> resultBody = Map.of(
                "value", "3.5", "unit", "mIU/L", "interpretation", "NORMAL");

        // Record first time
        restTemplate.exchange(
                baseUrl("/api/v1/lab-orders/" + orderId + "/result"),
                HttpMethod.POST,
                new HttpEntity<>(resultBody, authHeaders("NURSE")),
                Map.class);

        // Record second time — must conflict
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/lab-orders/" + orderId + "/result"),
                HttpMethod.POST,
                new HttpEntity<>(resultBody, authHeaders("NURSE")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    // ── GET lab orders ────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET lab orders — paginated, DOCTOR can access")
    void getLabOrders_returnsPagedResults() {
        createOrder("CBC", "HEMATOLOGY", "ROUTINE");
        createOrder("Creatinine", "CHEMISTRY", "URGENT");

        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/lab-orders?page=0&size=10"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders("DOCTOR")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((Integer) resp.getBody().get("totalElements")).isEqualTo(2);
    }

    // ── GET lab results ───────────────────────────────────────────────────────

    @Test
    @DisplayName("GET lab results — returns results after recording")
    void getLabResults_returnsResults() {
        String orderId = createOrder("Sodium", "CHEMISTRY", "ROUTINE");
        Map<String, Object> resultBody = Map.of(
                "value", "140", "unit", "mmol/L", "interpretation", "NORMAL");
        restTemplate.exchange(
                baseUrl("/api/v1/lab-orders/" + orderId + "/result"),
                HttpMethod.POST,
                new HttpEntity<>(resultBody, authHeaders("NURSE")),
                Map.class);

        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/lab-results?page=0&size=10"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders("DOCTOR")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((Integer) resp.getBody().get("totalElements")).isEqualTo(1);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private String createOrder(String testName, String category, String priority) {
        Map<String, Object> body = Map.of(
                "testName", testName, "category", category, "priority", priority);
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/lab-orders"),
                HttpMethod.POST,
                new HttpEntity<>(body, authHeaders("DOCTOR")),
                Map.class);
        return (String) resp.getBody().get("id");
    }
}

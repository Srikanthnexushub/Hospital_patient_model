package com.ainexus.hospital.patient.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full RBAC matrix for Module 6 — Clinical Intelligence & Safety.
 *
 * Endpoints under test (13 total):
 *  1. POST /patients/{id}/lab-orders           → DOCTOR, ADMIN only
 *  2. GET  /patients/{id}/lab-orders           → DOCTOR, NURSE, ADMIN
 *  3. POST /lab-orders/{id}/result             → NURSE, DOCTOR, ADMIN
 *  4. GET  /patients/{id}/lab-results          → DOCTOR, NURSE, ADMIN
 *  5. GET  /patients/{id}/news2                → DOCTOR, NURSE, ADMIN
 *  6. POST /patients/{id}/interaction-check    → DOCTOR, ADMIN only
 *  7. GET  /patients/{id}/interaction-summary  → DOCTOR, NURSE, ADMIN
 *  8. GET  /patients/{id}/alerts               → DOCTOR, NURSE, ADMIN
 *  9. GET  /alerts                             → DOCTOR, NURSE, ADMIN
 * 10. PATCH /alerts/{id}/acknowledge           → DOCTOR, NURSE, ADMIN
 * 11. PATCH /alerts/{id}/dismiss               → DOCTOR, NURSE, ADMIN
 * 12. GET  /dashboard/patient-risk             → DOCTOR, ADMIN only
 * 13. GET  /dashboard/stats                    → DOCTOR, ADMIN only
 */
@Tag("clinical-intelligence")
@Tag("rbac")
@DisplayName("Module 6 — RBAC Matrix")
class ClinicalIntelligenceRbacIT extends BaseIntegrationTest {

    private String patientId;
    private UUID alertId;
    private UUID labOrderId;

    @BeforeEach
    void setup() {
        patientId = seedPatient("Rbac", "ClinIntel");
        alertId   = seedAlert(patientId);
        labOrderId = seedLabOrder(patientId);
    }

    // =========================================================================
    // 1. POST /patients/{id}/lab-orders
    // =========================================================================

    @Test @DisplayName("POST lab-orders: DOCTOR → 201")
    void labOrder_doctor_201() {
        assertThat(postLabOrder("DOCTOR").getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test @DisplayName("POST lab-orders: ADMIN → 201")
    void labOrder_admin_201() {
        assertThat(postLabOrder("ADMIN").getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test @DisplayName("POST lab-orders: NURSE → 403")
    void labOrder_nurse_403() {
        assertThat(postLabOrder("NURSE").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test @DisplayName("POST lab-orders: RECEPTIONIST → 403")
    void labOrder_receptionist_403() {
        assertThat(postLabOrder("RECEPTIONIST").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // =========================================================================
    // 2. GET /patients/{id}/lab-orders
    // =========================================================================

    @Test @DisplayName("GET lab-orders: DOCTOR → 200")
    void getLabOrders_doctor_200() {
        assertThat(get("/api/v1/patients/" + patientId + "/lab-orders", "DOCTOR").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test @DisplayName("GET lab-orders: NURSE → 200")
    void getLabOrders_nurse_200() {
        assertThat(get("/api/v1/patients/" + patientId + "/lab-orders", "NURSE").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test @DisplayName("GET lab-orders: ADMIN → 200")
    void getLabOrders_admin_200() {
        assertThat(get("/api/v1/patients/" + patientId + "/lab-orders", "ADMIN").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test @DisplayName("GET lab-orders: RECEPTIONIST → 403")
    void getLabOrders_receptionist_403() {
        assertThat(get("/api/v1/patients/" + patientId + "/lab-orders", "RECEPTIONIST").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // =========================================================================
    // 3. POST /lab-orders/{id}/result
    // =========================================================================

    @Test @DisplayName("POST result: NURSE → 201")
    void recordResult_nurse_201() {
        UUID orderId = createOrderViaApi();
        assertThat(postResult(orderId, "NURSE").getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test @DisplayName("POST result: DOCTOR → 201")
    void recordResult_doctor_201() {
        UUID orderId = createOrderViaApi();
        assertThat(postResult(orderId, "DOCTOR").getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test @DisplayName("POST result: ADMIN → 201")
    void recordResult_admin_201() {
        UUID orderId = createOrderViaApi();
        assertThat(postResult(orderId, "ADMIN").getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test @DisplayName("POST result: RECEPTIONIST → 403")
    void recordResult_receptionist_403() {
        assertThat(postResult(labOrderId, "RECEPTIONIST").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // =========================================================================
    // 4. GET /patients/{id}/lab-results
    // =========================================================================

    @Test @DisplayName("GET lab-results: DOCTOR → 200")
    void getLabResults_doctor_200() {
        assertThat(get("/api/v1/patients/" + patientId + "/lab-results", "DOCTOR").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test @DisplayName("GET lab-results: NURSE → 200")
    void getLabResults_nurse_200() {
        assertThat(get("/api/v1/patients/" + patientId + "/lab-results", "NURSE").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test @DisplayName("GET lab-results: ADMIN → 200")
    void getLabResults_admin_200() {
        assertThat(get("/api/v1/patients/" + patientId + "/lab-results", "ADMIN").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test @DisplayName("GET lab-results: RECEPTIONIST → 403")
    void getLabResults_receptionist_403() {
        assertThat(get("/api/v1/patients/" + patientId + "/lab-results", "RECEPTIONIST").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // =========================================================================
    // 5. GET /patients/{id}/news2
    // =========================================================================

    @Test @DisplayName("GET news2: DOCTOR → 200")
    void news2_doctor_200() {
        assertThat(get("/api/v1/patients/" + patientId + "/news2", "DOCTOR").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test @DisplayName("GET news2: NURSE → 200")
    void news2_nurse_200() {
        assertThat(get("/api/v1/patients/" + patientId + "/news2", "NURSE").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test @DisplayName("GET news2: ADMIN → 200")
    void news2_admin_200() {
        assertThat(get("/api/v1/patients/" + patientId + "/news2", "ADMIN").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test @DisplayName("GET news2: RECEPTIONIST → 403")
    void news2_receptionist_403() {
        assertThat(get("/api/v1/patients/" + patientId + "/news2", "RECEPTIONIST").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // =========================================================================
    // 6. POST /patients/{id}/interaction-check
    // =========================================================================

    @Test @DisplayName("POST interaction-check: DOCTOR → 200")
    void interactionCheck_doctor_200() {
        assertThat(postInteractionCheck("DOCTOR").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test @DisplayName("POST interaction-check: ADMIN → 200")
    void interactionCheck_admin_200() {
        assertThat(postInteractionCheck("ADMIN").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test @DisplayName("POST interaction-check: NURSE → 403")
    void interactionCheck_nurse_403() {
        assertThat(postInteractionCheck("NURSE").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test @DisplayName("POST interaction-check: RECEPTIONIST → 403")
    void interactionCheck_receptionist_403() {
        assertThat(postInteractionCheck("RECEPTIONIST").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // =========================================================================
    // 7. GET /patients/{id}/interaction-summary
    // =========================================================================

    @Test @DisplayName("GET interaction-summary: DOCTOR → 200")
    void interactionSummary_doctor_200() {
        assertThat(get("/api/v1/patients/" + patientId + "/interaction-summary", "DOCTOR").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test @DisplayName("GET interaction-summary: NURSE → 200")
    void interactionSummary_nurse_200() {
        assertThat(get("/api/v1/patients/" + patientId + "/interaction-summary", "NURSE").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test @DisplayName("GET interaction-summary: ADMIN → 200")
    void interactionSummary_admin_200() {
        assertThat(get("/api/v1/patients/" + patientId + "/interaction-summary", "ADMIN").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test @DisplayName("GET interaction-summary: RECEPTIONIST → 403")
    void interactionSummary_receptionist_403() {
        assertThat(get("/api/v1/patients/" + patientId + "/interaction-summary", "RECEPTIONIST").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // =========================================================================
    // 8. GET /patients/{id}/alerts
    // =========================================================================

    @Test @DisplayName("GET patient alerts: DOCTOR → 200")
    void patientAlerts_doctor_200() {
        assertThat(get("/api/v1/patients/" + patientId + "/alerts", "DOCTOR").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test @DisplayName("GET patient alerts: NURSE → 200")
    void patientAlerts_nurse_200() {
        assertThat(get("/api/v1/patients/" + patientId + "/alerts", "NURSE").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test @DisplayName("GET patient alerts: ADMIN → 200")
    void patientAlerts_admin_200() {
        assertThat(get("/api/v1/patients/" + patientId + "/alerts", "ADMIN").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test @DisplayName("GET patient alerts: RECEPTIONIST → 403")
    void patientAlerts_receptionist_403() {
        assertThat(get("/api/v1/patients/" + patientId + "/alerts", "RECEPTIONIST").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // =========================================================================
    // 9. GET /alerts (global)
    // =========================================================================

    @Test @DisplayName("GET global alerts: DOCTOR → 200")
    void globalAlerts_doctor_200() {
        assertThat(get("/api/v1/alerts", "DOCTOR").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test @DisplayName("GET global alerts: NURSE → 200")
    void globalAlerts_nurse_200() {
        assertThat(get("/api/v1/alerts", "NURSE").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test @DisplayName("GET global alerts: ADMIN → 200")
    void globalAlerts_admin_200() {
        assertThat(get("/api/v1/alerts", "ADMIN").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test @DisplayName("GET global alerts: RECEPTIONIST → 403")
    void globalAlerts_receptionist_403() {
        assertThat(get("/api/v1/alerts", "RECEPTIONIST").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // =========================================================================
    // 10. PATCH /alerts/{id}/acknowledge
    // =========================================================================

    @Test @DisplayName("Acknowledge: DOCTOR → 200")
    void acknowledge_doctor_200() {
        UUID id = seedAlert(patientId);
        assertThat(patch("/api/v1/alerts/" + id + "/acknowledge", null, "DOCTOR").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test @DisplayName("Acknowledge: NURSE → 200")
    void acknowledge_nurse_200() {
        UUID id = seedAlert(patientId);
        assertThat(patch("/api/v1/alerts/" + id + "/acknowledge", null, "NURSE").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test @DisplayName("Acknowledge: ADMIN → 200")
    void acknowledge_admin_200() {
        UUID id = seedAlert(patientId);
        assertThat(patch("/api/v1/alerts/" + id + "/acknowledge", null, "ADMIN").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test @DisplayName("Acknowledge: RECEPTIONIST → 403")
    void acknowledge_receptionist_403() {
        assertThat(patch("/api/v1/alerts/" + alertId + "/acknowledge", null, "RECEPTIONIST").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // =========================================================================
    // 11. PATCH /alerts/{id}/dismiss
    // =========================================================================

    @Test @DisplayName("Dismiss: DOCTOR → 200")
    void dismiss_doctor_200() {
        UUID id = seedAlert(patientId);
        assertThat(patch("/api/v1/alerts/" + id + "/dismiss", Map.of("reason", "Resolved"), "DOCTOR").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test @DisplayName("Dismiss: NURSE → 200")
    void dismiss_nurse_200() {
        UUID id = seedAlert(patientId);
        assertThat(patch("/api/v1/alerts/" + id + "/dismiss", Map.of("reason", "Resolved"), "NURSE").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test @DisplayName("Dismiss: ADMIN → 200")
    void dismiss_admin_200() {
        UUID id = seedAlert(patientId);
        assertThat(patch("/api/v1/alerts/" + id + "/dismiss", Map.of("reason", "Resolved"), "ADMIN").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test @DisplayName("Dismiss: RECEPTIONIST → 403")
    void dismiss_receptionist_403() {
        assertThat(patch("/api/v1/alerts/" + alertId + "/dismiss",
                Map.of("reason", "attempt"), "RECEPTIONIST").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // =========================================================================
    // 12. GET /dashboard/patient-risk
    // =========================================================================

    @Test @DisplayName("Dashboard patient-risk: DOCTOR → 200")
    void dashboardRisk_doctor_200() {
        assertThat(get("/api/v1/dashboard/patient-risk", "DOCTOR").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test @DisplayName("Dashboard patient-risk: ADMIN → 200")
    void dashboardRisk_admin_200() {
        assertThat(get("/api/v1/dashboard/patient-risk", "ADMIN").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test @DisplayName("Dashboard patient-risk: NURSE → 403")
    void dashboardRisk_nurse_403() {
        assertThat(get("/api/v1/dashboard/patient-risk", "NURSE").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test @DisplayName("Dashboard patient-risk: RECEPTIONIST → 403")
    void dashboardRisk_receptionist_403() {
        assertThat(get("/api/v1/dashboard/patient-risk", "RECEPTIONIST").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // =========================================================================
    // 13. GET /dashboard/stats
    // =========================================================================

    @Test @DisplayName("Dashboard stats: DOCTOR → 200")
    void dashboardStats_doctor_200() {
        assertThat(get("/api/v1/dashboard/stats", "DOCTOR").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test @DisplayName("Dashboard stats: ADMIN → 200")
    void dashboardStats_admin_200() {
        assertThat(get("/api/v1/dashboard/stats", "ADMIN").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test @DisplayName("Dashboard stats: NURSE → 403")
    void dashboardStats_nurse_403() {
        assertThat(get("/api/v1/dashboard/stats", "NURSE").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test @DisplayName("Dashboard stats: RECEPTIONIST → 403")
    void dashboardStats_receptionist_403() {
        assertThat(get("/api/v1/dashboard/stats", "RECEPTIONIST").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private ResponseEntity<Map> get(String path, String role) {
        return restTemplate.exchange(baseUrl(path), HttpMethod.GET,
                new HttpEntity<>(authHeaders(role)), Map.class);
    }

    private ResponseEntity<Map> patch(String path, Map<?, ?> body, String role) {
        HttpHeaders headers = authHeaders(role);
        return restTemplate.exchange(baseUrl(path), HttpMethod.PATCH,
                new HttpEntity<>(body, headers), Map.class);
    }

    private ResponseEntity<Map> postLabOrder(String role) {
        Map<String, Object> body = Map.of(
                "testName", "FBC", "category", "HEMATOLOGY", "priority", "ROUTINE");
        return restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/lab-orders"),
                HttpMethod.POST, new HttpEntity<>(body, authHeaders(role)), Map.class);
    }

    private ResponseEntity<Map> postResult(UUID orderId, String role) {
        Map<String, Object> body = Map.of(
                "value", "5.0", "unit", "mmol/L", "interpretation", "NORMAL");
        return restTemplate.exchange(
                baseUrl("/api/v1/lab-orders/" + orderId + "/result"),
                HttpMethod.POST, new HttpEntity<>(body, authHeaders(role)), Map.class);
    }

    private ResponseEntity<Map> postInteractionCheck(String role) {
        Map<String, Object> body = Map.of("drugName", "paracetamol");
        return restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/interaction-check"),
                HttpMethod.POST, new HttpEntity<>(body, authHeaders(role)), Map.class);
    }

    private UUID seedAlert(String pid) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO clinical_alerts
                  (id, patient_id, alert_type, severity, title, description, source, status, created_at)
                VALUES (?, ?, 'LAB_CRITICAL', 'CRITICAL', 'Test', 'Desc', 'Test', 'ACTIVE', NOW())
                """, id, pid);
        return id;
    }

    /** Seeds a PENDING lab order directly and returns its UUID. */
    private UUID seedLabOrder(String pid) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO lab_orders
                  (id, patient_id, test_name, category, priority, status, ordered_by, ordered_at)
                VALUES (?, ?, 'Test', 'HEMATOLOGY', 'ROUTINE', 'PENDING', 'doctor1', NOW())
                """, id, pid);
        return id;
    }

    /** Creates a lab order via the API and returns its UUID. */
    private UUID createOrderViaApi() {
        Map<String, Object> body = Map.of(
                "testName", "Glucose", "category", "CHEMISTRY", "priority", "ROUTINE");
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/lab-orders"),
                HttpMethod.POST, new HttpEntity<>(body, authHeaders("DOCTOR")), Map.class);
        return UUID.fromString((String) resp.getBody().get("id"));
    }
}

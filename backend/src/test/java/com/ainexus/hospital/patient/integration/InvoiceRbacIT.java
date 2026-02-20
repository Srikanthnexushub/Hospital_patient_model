package com.ainexus.hospital.patient.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full RBAC matrix for all 7 billing endpoints.
 *
 * Endpoint            RECEPTIONIST  DOCTOR  NURSE  ADMIN
 * POST /invoices          ✓           ✗       ✗      ✓
 * GET  /invoices          ✓           ✓       ✗      ✓
 * GET  /invoices/{id}     ✓           ✓*      ✗      ✓
 * POST /invoices/{id}/pay ✓           ✗       ✗      ✓
 * PATCH /invoices/{id}/st ✗           ✗       ✗      ✓
 * GET  /patients/{id}/inv ✓           ✓       ✗      ✓
 * GET  /reports/financial ✗           ✗       ✗      ✓
 *
 * (* DOCTOR only sees own patients' invoices — tested in InvoiceSearchIT)
 */
@Tag("billing")
@DisplayName("Invoice RBAC — Full Role Matrix")
class InvoiceRbacIT extends BaseIntegrationTest {

    private String patientId;
    private String invoiceId;

    @BeforeEach
    void setupInvoice() {
        String doctorId = seedDoctorWithId("U2025001", "drsmith");
        patientId = seedPatient("Rbac", "Invoice");
        String apptId = seedAppointment(patientId, doctorId, LocalDate.of(2025, 11, 1));
        invoiceId = createInvoice(apptId, 100.0);
        setInvoiceStatus(invoiceId, "ISSUED");
    }

    // ── POST /api/v1/invoices ───────────────────────────────────────────────

    @Test
    @DisplayName("POST /invoices: ADMIN allowed — 201")
    void createInvoice_admin_allowed() {
        String doctorId = seedDoctorWithId("U2025001", "drsmith");
        String pid = seedPatient("AdminCreate", "Rbac");
        String apptId = seedAppointment(pid, doctorId, LocalDate.of(2025, 11, 10));
        Map<String, Object> body = Map.of(
                "appointmentId", apptId,
                "lineItems", java.util.List.of(Map.of("description", "Test", "quantity", 1, "unitPrice", 50.0)));
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/invoices"), HttpMethod.POST,
                new HttpEntity<>(body, authHeaders("ADMIN")), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("POST /invoices: RECEPTIONIST allowed — 201")
    void createInvoice_receptionist_allowed() {
        String doctorId = seedDoctorWithId("U2025001", "drsmith");
        String pid = seedPatient("ReceptCreate", "Rbac");
        String apptId = seedAppointment(pid, doctorId, LocalDate.of(2025, 11, 11));
        Map<String, Object> body = Map.of(
                "appointmentId", apptId,
                "lineItems", java.util.List.of(Map.of("description", "Test", "quantity", 1, "unitPrice", 50.0)));
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/invoices"), HttpMethod.POST,
                new HttpEntity<>(body, authHeaders("RECEPTIONIST")), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("POST /invoices: DOCTOR denied — 403")
    void createInvoice_doctor_forbidden() {
        Map<String, Object> body = Map.of(
                "appointmentId", "APT2025FAKE",
                "lineItems", java.util.List.of(Map.of("description", "Test", "quantity", 1, "unitPrice", 50.0)));
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/invoices"), HttpMethod.POST,
                new HttpEntity<>(body, authHeaders("DOCTOR")), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("POST /invoices: NURSE denied — 403")
    void createInvoice_nurse_forbidden() {
        Map<String, Object> body = Map.of(
                "appointmentId", "APT2025FAKE",
                "lineItems", java.util.List.of(Map.of("description", "Test", "quantity", 1, "unitPrice", 50.0)));
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/invoices"), HttpMethod.POST,
                new HttpEntity<>(body, authHeaders("NURSE")), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── GET /api/v1/invoices ────────────────────────────────────────────────

    @Test
    @DisplayName("GET /invoices: ADMIN allowed — 200")
    void listInvoices_admin_allowed() {
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/invoices"), HttpMethod.GET,
                new HttpEntity<>(authHeaders("ADMIN")), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("GET /invoices: RECEPTIONIST allowed — 200")
    void listInvoices_receptionist_allowed() {
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/invoices"), HttpMethod.GET,
                new HttpEntity<>(authHeaders("RECEPTIONIST")), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("GET /invoices: DOCTOR allowed — 200")
    void listInvoices_doctor_allowed() {
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/invoices"), HttpMethod.GET,
                new HttpEntity<>(authHeaders("DOCTOR")), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("GET /invoices: NURSE denied — 403")
    void listInvoices_nurse_forbidden() {
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/invoices"), HttpMethod.GET,
                new HttpEntity<>(authHeaders("NURSE")), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── GET /api/v1/invoices/{id} ───────────────────────────────────────────

    @Test
    @DisplayName("GET /invoices/{id}: ADMIN allowed — 200")
    void getInvoice_admin_allowed() {
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/invoices/" + invoiceId), HttpMethod.GET,
                new HttpEntity<>(authHeaders("ADMIN")), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("GET /invoices/{id}: RECEPTIONIST allowed — 200")
    void getInvoice_receptionist_allowed() {
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/invoices/" + invoiceId), HttpMethod.GET,
                new HttpEntity<>(authHeaders("RECEPTIONIST")), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("GET /invoices/{id}: NURSE denied — 403")
    void getInvoice_nurse_forbidden() {
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/invoices/" + invoiceId), HttpMethod.GET,
                new HttpEntity<>(authHeaders("NURSE")), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── POST /api/v1/invoices/{id}/payments ────────────────────────────────

    @Test
    @DisplayName("POST /invoices/{id}/payments: ADMIN allowed — 200")
    void recordPayment_admin_allowed() {
        Map<String, Object> body = Map.of("amount", 10.0, "paymentMethod", "CASH");
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/invoices/" + invoiceId + "/payments"),
                HttpMethod.POST,
                new HttpEntity<>(body, authHeaders("ADMIN")),
                Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("POST /invoices/{id}/payments: DOCTOR denied — 403")
    void recordPayment_doctor_forbidden() {
        Map<String, Object> body = Map.of("amount", 10.0, "paymentMethod", "CASH");
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/invoices/" + invoiceId + "/payments"),
                HttpMethod.POST,
                new HttpEntity<>(body, authHeaders("DOCTOR")),
                Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("POST /invoices/{id}/payments: NURSE denied — 403")
    void recordPayment_nurse_forbidden() {
        Map<String, Object> body = Map.of("amount", 10.0, "paymentMethod", "CASH");
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/invoices/" + invoiceId + "/payments"),
                HttpMethod.POST,
                new HttpEntity<>(body, authHeaders("NURSE")),
                Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── PATCH /api/v1/invoices/{id}/status ─────────────────────────────────

    @Test
    @DisplayName("PATCH /invoices/{id}/status: ADMIN allowed — 200")
    void updateStatus_admin_allowed() {
        Map<String, Object> body = Map.of("action", "CANCEL", "reason", "RBAC test");
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/invoices/" + invoiceId + "/status"),
                HttpMethod.PATCH,
                new HttpEntity<>(body, authHeaders("ADMIN")),
                Map.class);
        // ISSUED → CANCEL is valid
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("PATCH /invoices/{id}/status: RECEPTIONIST denied — 403")
    void updateStatus_receptionist_forbidden() {
        Map<String, Object> body = Map.of("action", "CANCEL", "reason", "Attempt");
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/invoices/" + invoiceId + "/status"),
                HttpMethod.PATCH,
                new HttpEntity<>(body, authHeaders("RECEPTIONIST")),
                Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("PATCH /invoices/{id}/status: DOCTOR denied — 403")
    void updateStatus_doctor_forbidden() {
        Map<String, Object> body = Map.of("action", "CANCEL", "reason", "Attempt");
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/invoices/" + invoiceId + "/status"),
                HttpMethod.PATCH,
                new HttpEntity<>(body, authHeaders("DOCTOR")),
                Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("PATCH /invoices/{id}/status: NURSE denied — 403")
    void updateStatus_nurse_forbidden() {
        Map<String, Object> body = Map.of("action", "CANCEL", "reason", "Attempt");
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/invoices/" + invoiceId + "/status"),
                HttpMethod.PATCH,
                new HttpEntity<>(body, authHeaders("NURSE")),
                Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── GET /api/v1/patients/{id}/invoices ─────────────────────────────────

    @Test
    @DisplayName("GET /patients/{id}/invoices: ADMIN allowed — 200")
    void listPatientInvoices_admin_allowed() {
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/invoices"), HttpMethod.GET,
                new HttpEntity<>(authHeaders("ADMIN")), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("GET /patients/{id}/invoices: RECEPTIONIST allowed — 200")
    void listPatientInvoices_receptionist_allowed() {
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/invoices"), HttpMethod.GET,
                new HttpEntity<>(authHeaders("RECEPTIONIST")), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("GET /patients/{id}/invoices: DOCTOR allowed — 200")
    void listPatientInvoices_doctor_allowed() {
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/invoices"), HttpMethod.GET,
                new HttpEntity<>(authHeaders("DOCTOR")), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("GET /patients/{id}/invoices: NURSE denied — 403")
    void listPatientInvoices_nurse_forbidden() {
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/invoices"), HttpMethod.GET,
                new HttpEntity<>(authHeaders("NURSE")), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── GET /api/v1/reports/financial ──────────────────────────────────────

    @Test
    @DisplayName("GET /reports/financial: ADMIN allowed — 200")
    void financialReport_admin_allowed() {
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/reports/financial?dateFrom=2025-01-01&dateTo=2025-12-31"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders("ADMIN")),
                Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("GET /reports/financial: RECEPTIONIST denied — 403")
    void financialReport_receptionist_forbidden() {
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/reports/financial?dateFrom=2025-01-01&dateTo=2025-12-31"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders("RECEPTIONIST")),
                Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("GET /reports/financial: DOCTOR denied — 403")
    void financialReport_doctor_forbidden() {
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/reports/financial?dateFrom=2025-01-01&dateTo=2025-12-31"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders("DOCTOR")),
                Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("GET /reports/financial: NURSE denied — 403")
    void financialReport_nurse_forbidden() {
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/reports/financial?dateFrom=2025-01-01&dateTo=2025-12-31"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders("NURSE")),
                Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}

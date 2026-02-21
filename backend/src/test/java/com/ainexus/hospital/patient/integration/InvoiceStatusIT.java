package com.ainexus.hospital.patient.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("billing")
@DisplayName("Invoice Status — US4: Cancel / Write-off Invoice")
class InvoiceStatusIT extends BaseIntegrationTest {

    private ResponseEntity<Map> patchStatus(String invoiceId, String action, String reason) {
        Map<String, Object> body = Map.of("action", action, "reason", reason);
        return restTemplate.exchange(
                baseUrl("/api/v1/invoices/" + invoiceId + "/status"),
                HttpMethod.PATCH,
                new HttpEntity<>(body, authHeaders("ADMIN")),
                Map.class);
    }

    private ResponseEntity<Map> patchStatusAs(String invoiceId, String action, String reason, String role) {
        Map<String, Object> body = Map.of("action", action, "reason", reason);
        return restTemplate.exchange(
                baseUrl("/api/v1/invoices/" + invoiceId + "/status"),
                HttpMethod.PATCH,
                new HttpEntity<>(body, authHeaders(role)),
                Map.class);
    }

    private String setupDraftInvoice() {
        String doctorId = seedDoctorWithId("U2025001", "drsmith");
        String patientId = seedPatient("Cancel", "Test" + (int)(Math.random() * 1000));
        String apptId = seedAppointment(patientId, doctorId, LocalDate.of(2025, 7, 1));
        return createInvoice(apptId, 200.0);
    }

    private String setupIssuedInvoice() {
        String doctorId = seedDoctorWithId("U2025001", "drsmith");
        String patientId = seedPatient("Issued", "Test" + (int)(Math.random() * 1000));
        String apptId = seedAppointment(patientId, doctorId, LocalDate.of(2025, 7, 2));
        String invoiceId = createInvoice(apptId, 300.0);
        setInvoiceStatus(invoiceId, "ISSUED");
        return invoiceId;
    }

    @Test
    @DisplayName("US4-S1: ADMIN can cancel a DRAFT invoice — transitions to CANCELLED")
    void cancelInvoice_fromDraft_becomesCancelled() {
        String invoiceId = setupDraftInvoice();

        ResponseEntity<Map> resp = patchStatus(invoiceId, "CANCEL", "Patient cancelled appointment");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("status")).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("US4-S2: ADMIN can cancel an ISSUED invoice — transitions to CANCELLED")
    void cancelInvoice_fromIssued_becomesCancelled() {
        String invoiceId = setupIssuedInvoice();

        ResponseEntity<Map> resp = patchStatus(invoiceId, "CANCEL", "Billing error");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("status")).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("US4-S3: Cancel a PAID invoice returns 409")
    void cancelInvoice_fromPaid_returns409() {
        String doctorId = seedDoctorWithId("U2025001", "drsmith");
        String patientId = seedPatient("PaidCancel", "Test");
        String apptId = seedAppointment(patientId, doctorId, LocalDate.of(2025, 7, 3));
        String invoiceId = createInvoice(apptId, 100.0);
        setInvoiceStatus(invoiceId, "PAID");

        ResponseEntity<Map> resp = patchStatus(invoiceId, "CANCEL", "Should not cancel paid");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("US4-S4: ADMIN can write-off an ISSUED invoice — transitions to WRITTEN_OFF")
    void writeOffInvoice_fromIssued_becomesWrittenOff() {
        String invoiceId = setupIssuedInvoice();

        ResponseEntity<Map> resp = patchStatus(invoiceId, "WRITE_OFF", "Uncollectable debt");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("status")).isEqualTo("WRITTEN_OFF");
    }

    @Test
    @DisplayName("US4-S5: ADMIN can write-off a PARTIALLY_PAID invoice — transitions to WRITTEN_OFF")
    void writeOffInvoice_fromPartiallyPaid_becomesWrittenOff() {
        String invoiceId = setupIssuedInvoice();
        // Make it partially paid
        Map<String, Object> paymentBody = Map.of("amount", 50.0, "paymentMethod", "CASH");
        restTemplate.exchange(
                baseUrl("/api/v1/invoices/" + invoiceId + "/payments"),
                HttpMethod.POST,
                new HttpEntity<>(paymentBody, authHeaders("RECEPTIONIST")),
                Map.class);

        ResponseEntity<Map> resp = patchStatus(invoiceId, "WRITE_OFF", "Bad debt write-off");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("status")).isEqualTo("WRITTEN_OFF");
    }

    @Test
    @DisplayName("US4-S6: Write-off a PAID invoice returns 409")
    void writeOffInvoice_fromPaid_returns409() {
        String doctorId = seedDoctorWithId("U2025001", "drsmith");
        String patientId = seedPatient("PaidWO", "Test");
        String apptId = seedAppointment(patientId, doctorId, LocalDate.of(2025, 7, 4));
        String invoiceId = createInvoice(apptId, 100.0);
        setInvoiceStatus(invoiceId, "PAID");

        ResponseEntity<Map> resp = patchStatus(invoiceId, "WRITE_OFF", "Should fail");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("US4-S7: Write-off a CANCELLED invoice returns 409")
    void writeOffInvoice_fromCancelled_returns409() {
        String invoiceId = setupDraftInvoice();
        setInvoiceStatus(invoiceId, "CANCELLED");

        ResponseEntity<Map> resp = patchStatus(invoiceId, "WRITE_OFF", "Should fail");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("US4-S8: RECEPTIONIST denied cancel — 403")
    void cancelInvoice_receptionistDenied_403() {
        String invoiceId = setupDraftInvoice();

        ResponseEntity<Map> resp = patchStatusAs(invoiceId, "CANCEL", "Attempt by receptionist", "RECEPTIONIST");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("US4-S9: DOCTOR denied cancel — 403")
    void cancelInvoice_doctorDenied_403() {
        String invoiceId = setupDraftInvoice();

        ResponseEntity<Map> resp = patchStatusAs(invoiceId, "CANCEL", "Attempt by doctor", "DOCTOR");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("US4-S10: Audit log entry created on cancellation")
    void cancelInvoice_auditLogCreated() {
        String invoiceId = setupDraftInvoice();
        patchStatus(invoiceId, "CANCEL", "Audit test cancellation");

        int auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM invoice_audit_log WHERE invoice_id = ? AND action = 'CANCEL'",
                Integer.class, invoiceId);
        assertThat(auditCount).isEqualTo(1);
    }
}

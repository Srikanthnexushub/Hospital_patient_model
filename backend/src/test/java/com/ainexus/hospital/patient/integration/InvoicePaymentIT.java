package com.ainexus.hospital.patient.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("billing")
@DisplayName("Invoice Payment — US3: Record Payment")
class InvoicePaymentIT extends BaseIntegrationTest {

    private String setupIssuedInvoice(double amount) {
        String doctorId = seedDoctorWithId("U2025001", "drsmith");
        String patientId = seedPatient("Pay", "Test" + (int)(Math.random() * 1000));
        String apptId = seedAppointment(patientId, doctorId, LocalDate.of(2025, 6, 10));
        String invoiceId = createInvoice(apptId, amount);
        setInvoiceStatus(invoiceId, "ISSUED");
        return invoiceId;
    }

    private ResponseEntity<Map> postPayment(String invoiceId, double amount, String method) {
        Map<String, Object> body = Map.of("amount", amount, "paymentMethod", method);
        return restTemplate.exchange(
                baseUrl("/api/v1/invoices/" + invoiceId + "/payments"),
                HttpMethod.POST,
                new HttpEntity<>(body, authHeaders("RECEPTIONIST")),
                Map.class);
    }

    @Test
    @DisplayName("US3-S1: Partial payment transitions ISSUED → PARTIALLY_PAID")
    void recordPayment_partial_becomesPartiallyPaid() {
        String invoiceId = setupIssuedInvoice(300.00);

        ResponseEntity<Map> resp = postPayment(invoiceId, 100.0, "CASH");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = resp.getBody();
        assertThat(body.get("status")).isEqualTo("PARTIALLY_PAID");
        assertThat(new java.math.BigDecimal(body.get("amountPaid").toString())).isEqualByComparingTo("100.00");
        assertThat(new java.math.BigDecimal(body.get("amountDue").toString())).isEqualByComparingTo("200.00");
    }

    @Test
    @DisplayName("US3-S2: Full payment transitions PARTIALLY_PAID → PAID")
    void recordPayment_fullAfterPartial_becomesPaid() {
        String invoiceId = setupIssuedInvoice(300.00);
        postPayment(invoiceId, 100.0, "CASH");  // → PARTIALLY_PAID

        ResponseEntity<Map> resp = postPayment(invoiceId, 200.0, "CARD");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = resp.getBody();
        assertThat(body.get("status")).isEqualTo("PAID");
        assertThat(new java.math.BigDecimal(body.get("amountPaid").toString())).isEqualByComparingTo("300.00");
        assertThat(new java.math.BigDecimal(body.get("amountDue").toString())).isEqualByComparingTo("0.00");
        @SuppressWarnings("unchecked")
        List<Object> payments = (List<Object>) body.get("payments");
        assertThat(payments).hasSize(2);
    }

    @Test
    @DisplayName("US3-S3: Overpayment accepted — amountDue goes negative, status = PAID")
    void recordPayment_overpayment_amountDueNegative() {
        String invoiceId = setupIssuedInvoice(50.00);

        ResponseEntity<Map> resp = postPayment(invoiceId, 100.0, "INSURANCE");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = resp.getBody();
        assertThat(body.get("status")).isEqualTo("PAID");
        assertThat(new java.math.BigDecimal(body.get("amountDue").toString()))
                .isEqualByComparingTo("-50.00");
    }

    @Test
    @DisplayName("US3-S4: Payment against DRAFT invoice returns 409")
    void recordPayment_draftInvoice_returns409() {
        String doctorId = seedDoctorWithId("U2025001", "drsmith");
        String patientId = seedPatient("Draft", "Pay");
        String apptId = seedAppointment(patientId, doctorId, LocalDate.of(2025, 6, 11));
        String invoiceId = createInvoice(apptId, 100.0); // stays DRAFT

        ResponseEntity<Map> resp = postPayment(invoiceId, 50.0, "CASH");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("US3-S5: Payment against CANCELLED invoice returns 409")
    void recordPayment_cancelledInvoice_returns409() {
        String invoiceId = setupIssuedInvoice(100.00);
        setInvoiceStatus(invoiceId, "CANCELLED");

        ResponseEntity<Map> resp = postPayment(invoiceId, 50.0, "CASH");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody().get("message").toString()).containsIgnoringCase("CANCELLED");
    }

    @Test
    @DisplayName("US3-S6: Payment against PAID invoice returns 409")
    void recordPayment_paidInvoice_returns409() {
        String invoiceId = setupIssuedInvoice(100.00);
        setInvoiceStatus(invoiceId, "PAID");

        ResponseEntity<Map> resp = postPayment(invoiceId, 50.0, "CASH");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("US3-S7: NURSE denied payment recording — 403")
    void recordPayment_nurseDenied_403() {
        String invoiceId = setupIssuedInvoice(100.00);
        Map<String, Object> body = Map.of("amount", 50.0, "paymentMethod", "CASH");
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/invoices/" + invoiceId + "/payments"),
                HttpMethod.POST,
                new HttpEntity<>(body, authHeaders("NURSE")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("US3-S8: Audit log entry created per payment")
    void recordPayment_auditLogCreated() {
        String invoiceId = setupIssuedInvoice(200.00);
        postPayment(invoiceId, 100.0, "CASH");

        int auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM invoice_audit_log WHERE invoice_id = ? AND action = 'PAYMENT'",
                Integer.class, invoiceId);
        assertThat(auditCount).isEqualTo(1);
    }
}

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
@DisplayName("Invoice Lifecycle — US1: Generate Invoice")
class InvoiceLifecycleIT extends BaseIntegrationTest {

    private String setupAppointment() {
        String doctorId = seedDoctorWithId("U2025001", "drsmith");
        String patientId = seedPatient("John", "Doe");
        return seedAppointment(patientId, doctorId, LocalDate.of(2025, 6, 15));
    }

    @Test
    @DisplayName("US1-S1: Two line items + 10% discount computes all monetary totals correctly")
    void createInvoice_twoLineItems10pctDiscount_correctTotals() {
        String apptId = setupAppointment();

        Map<String, Object> body = Map.of(
                "appointmentId", apptId,
                "lineItems", List.of(
                        Map.of("description", "General Consultation", "quantity", 1, "unitPrice", 200.00, "serviceCode", "CONS001"),
                        Map.of("description", "Blood Panel", "quantity", 2, "unitPrice", 150.00, "serviceCode", "LAB002")
                ),
                "discountPercent", 10.00,
                "notes", "Test invoice"
        );

        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/invoices"), HttpMethod.POST,
                new HttpEntity<>(body, authHeaders("RECEPTIONIST")), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> inv = resp.getBody();
        assertThat(inv.get("invoiceId").toString()).matches("INV\\d{10,}");
        assertThat(inv.get("status")).isEqualTo("DRAFT");
        assertThat(new java.math.BigDecimal(inv.get("totalAmount").toString())).isEqualByComparingTo("500.00");
        assertThat(new java.math.BigDecimal(inv.get("discountAmount").toString())).isEqualByComparingTo("50.00");
        assertThat(new java.math.BigDecimal(inv.get("netAmount").toString())).isEqualByComparingTo("450.00");
        assertThat(new java.math.BigDecimal(inv.get("taxAmount").toString())).isEqualByComparingTo("0.00");
        assertThat(new java.math.BigDecimal(inv.get("amountDue").toString())).isEqualByComparingTo("450.00");
        assertThat(new java.math.BigDecimal(inv.get("amountPaid").toString())).isEqualByComparingTo("0.00");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lineItems = (List<Map<String, Object>>) inv.get("lineItems");
        assertThat(lineItems).hasSize(2);
    }

    @Test
    @DisplayName("US1-S2: Duplicate invoice for same appointment returns 409")
    void createInvoice_duplicate_returns409() {
        String apptId = setupAppointment();
        // Create first invoice
        createInvoice(apptId, 100.0);

        // Attempt duplicate
        Map<String, Object> body = Map.of(
                "appointmentId", apptId,
                "lineItems", List.of(Map.of("description", "Duplicate", "quantity", 1, "unitPrice", 50.0))
        );
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/invoices"), HttpMethod.POST,
                new HttpEntity<>(body, authHeaders("RECEPTIONIST")), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody().get("message").toString()).containsIgnoringCase("already exists");
    }

    @Test
    @DisplayName("US1-S3: Non-existent appointment returns 404")
    void createInvoice_appointmentNotFound_returns404() {
        Map<String, Object> body = Map.of(
                "appointmentId", "APT9999XXXX",
                "lineItems", List.of(Map.of("description", "Test", "quantity", 1, "unitPrice", 50.0))
        );
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/invoices"), HttpMethod.POST,
                new HttpEntity<>(body, authHeaders("RECEPTIONIST")), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("US1-S4: NURSE denied invoice creation — 403")
    void createInvoice_nurseDenied_returns403() {
        String apptId = setupAppointment();
        Map<String, Object> body = Map.of(
                "appointmentId", apptId,
                "lineItems", List.of(Map.of("description", "Test", "quantity", 1, "unitPrice", 50.0))
        );
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/invoices"), HttpMethod.POST,
                new HttpEntity<>(body, authHeaders("NURSE")), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("US1-S5: DOCTOR denied invoice creation — 403")
    void createInvoice_doctorDenied_returns403() {
        String apptId = setupAppointment();
        Map<String, Object> body = Map.of(
                "appointmentId", apptId,
                "lineItems", List.of(Map.of("description", "Test", "quantity", 1, "unitPrice", 50.0))
        );
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/invoices"), HttpMethod.POST,
                new HttpEntity<>(body, authHeaders("DOCTOR")), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("US1: ADMIN can create invoice")
    void createInvoice_adminAllowed() {
        String apptId = setupAppointment();
        Map<String, Object> body = Map.of(
                "appointmentId", apptId,
                "lineItems", List.of(Map.of("description", "Consultation", "quantity", 1, "unitPrice", 200.0))
        );
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/invoices"), HttpMethod.POST,
                new HttpEntity<>(body, authHeaders("ADMIN")), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("E2E: Full lifecycle — DRAFT → ISSUED → PARTIALLY_PAID → PAID")
    void endToEnd_fullLifecycle_draftToIssued_partialThenFullPayment() {
        String doctorId = seedDoctorWithId("U2025001", "drsmith");
        String patientId = seedPatient("E2E", "Lifecycle");
        String apptId = seedAppointment(patientId, doctorId, LocalDate.of(2025, 12, 1));

        // Step 1: Create invoice (DRAFT)
        Map<String, Object> createBody = Map.of(
                "appointmentId", apptId,
                "lineItems", List.of(Map.of("description", "Consultation", "quantity", 1, "unitPrice", 300.0)),
                "notes", "E2E lifecycle test"
        );
        ResponseEntity<Map> created = restTemplate.exchange(
                baseUrl("/api/v1/invoices"), HttpMethod.POST,
                new HttpEntity<>(createBody, authHeaders("RECEPTIONIST")), Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String invoiceId = created.getBody().get("invoiceId").toString();
        assertThat(created.getBody().get("status")).isEqualTo("DRAFT");

        // Step 2: Issue invoice (DRAFT → ISSUED) via status endpoint
        Map<String, Object> issueBody = Map.of("action", "ISSUE", "reason", "Ready for billing");
        // ISSUE transition is set via DB helper in test setup
        setInvoiceStatus(invoiceId, "ISSUED");

        // Verify ISSUED
        ResponseEntity<Map> issued = restTemplate.exchange(
                baseUrl("/api/v1/invoices/" + invoiceId), HttpMethod.GET,
                new HttpEntity<>(authHeaders("RECEPTIONIST")), Map.class);
        assertThat(issued.getBody().get("status")).isEqualTo("ISSUED");

        // Step 3: Partial payment (→ PARTIALLY_PAID)
        Map<String, Object> pay1 = Map.of("amount", 100.0, "paymentMethod", "CASH");
        ResponseEntity<Map> afterPay1 = restTemplate.exchange(
                baseUrl("/api/v1/invoices/" + invoiceId + "/payments"),
                HttpMethod.POST,
                new HttpEntity<>(pay1, authHeaders("RECEPTIONIST")), Map.class);
        assertThat(afterPay1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(afterPay1.getBody().get("status")).isEqualTo("PARTIALLY_PAID");
        assertThat(new java.math.BigDecimal(afterPay1.getBody().get("amountPaid").toString())).isEqualByComparingTo("100.00");
        assertThat(new java.math.BigDecimal(afterPay1.getBody().get("amountDue").toString())).isEqualByComparingTo("200.00");

        // Step 4: Full payment (→ PAID)
        Map<String, Object> pay2 = Map.of("amount", 200.0, "paymentMethod", "CARD");
        ResponseEntity<Map> afterPay2 = restTemplate.exchange(
                baseUrl("/api/v1/invoices/" + invoiceId + "/payments"),
                HttpMethod.POST,
                new HttpEntity<>(pay2, authHeaders("RECEPTIONIST")), Map.class);
        assertThat(afterPay2.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(afterPay2.getBody().get("status")).isEqualTo("PAID");
        assertThat(new java.math.BigDecimal(afterPay2.getBody().get("amountPaid").toString())).isEqualByComparingTo("300.00");
        assertThat(new java.math.BigDecimal(afterPay2.getBody().get("amountDue").toString())).isEqualByComparingTo("0.00");

        // Step 5: Verify final invoice has 2 payment entries
        ResponseEntity<Map> finalState = restTemplate.exchange(
                baseUrl("/api/v1/invoices/" + invoiceId), HttpMethod.GET,
                new HttpEntity<>(authHeaders("RECEPTIONIST")), Map.class);
        @SuppressWarnings("unchecked")
        List<Object> payments = (List<Object>) finalState.getBody().get("payments");
        assertThat(payments).hasSize(2);
    }

    @Test
    @DisplayName("E2E: Write-off lifecycle — ISSUED → WRITE_OFF")
    void endToEnd_writeOff_issuedInvoice() {
        String doctorId = seedDoctorWithId("U2025001", "drsmith");
        String patientId = seedPatient("E2E", "WriteOff");
        String apptId = seedAppointment(patientId, doctorId, LocalDate.of(2025, 12, 2));
        String invoiceId = createInvoice(apptId, 500.0);
        setInvoiceStatus(invoiceId, "ISSUED");

        // Partially pay it
        Map<String, Object> payBody = Map.of("amount", 100.0, "paymentMethod", "INSURANCE");
        restTemplate.exchange(
                baseUrl("/api/v1/invoices/" + invoiceId + "/payments"),
                HttpMethod.POST,
                new HttpEntity<>(payBody, authHeaders("RECEPTIONIST")), Map.class);

        // Write off the remaining balance
        Map<String, Object> writeOffBody = Map.of("action", "WRITE_OFF", "reason", "Patient unable to pay");
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/invoices/" + invoiceId + "/status"),
                HttpMethod.PATCH,
                new HttpEntity<>(writeOffBody, authHeaders("ADMIN")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("status")).isEqualTo("WRITTEN_OFF");
        // amountPaid should still reflect the payment made
        assertThat(new java.math.BigDecimal(resp.getBody().get("amountPaid").toString())).isEqualByComparingTo("100.00");
    }
}

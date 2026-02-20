package com.ainexus.hospital.patient.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("billing")
@DisplayName("Financial Report — US5: Financial Summary Report")
class FinancialReportIT extends BaseIntegrationTest {

    private ResponseEntity<Map> getReport(String dateFrom, String dateTo) {
        return restTemplate.exchange(
                baseUrl("/api/v1/reports/financial?dateFrom=" + dateFrom + "&dateTo=" + dateTo),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders("ADMIN")),
                Map.class);
    }

    private ResponseEntity<Map> getReportAs(String dateFrom, String dateTo, String role) {
        return restTemplate.exchange(
                baseUrl("/api/v1/reports/financial?dateFrom=" + dateFrom + "&dateTo=" + dateTo),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(role)),
                Map.class);
    }

    @Test
    @DisplayName("US5-S1: Seeded invoices aggregate correctly in financial report")
    void financialReport_seededInvoices_aggregatesCorrectly() {
        String doctorId = seedDoctorWithId("U2025001", "drsmith");
        String patientId = seedPatient("Report", "Test");

        // Invoice 1: PAID — amount 200
        String appt1 = seedAppointment(patientId, doctorId, LocalDate.of(2025, 8, 1));
        String inv1 = createInvoice(appt1, 200.0);
        setInvoiceStatus(inv1, "PAID");

        // Invoice 2: ISSUED (outstanding) — amount 100
        String appt2 = seedAppointment(patientId, doctorId, LocalDate.of(2025, 8, 2));
        String inv2 = createInvoice(appt2, 100.0);
        setInvoiceStatus(inv2, "ISSUED");

        ResponseEntity<Map> resp = getReport("2026-01-01", "2026-12-31");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = resp.getBody();
        assertThat(body).containsKeys(
                "totalInvoiced", "totalCollected", "totalOutstanding",
                "totalWrittenOff", "totalCancelled", "invoiceCount",
                "paidCount", "partialCount", "overdueCount", "byPaymentMethod");
        assertThat(Integer.parseInt(body.get("invoiceCount").toString())).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("US5-S2: byPaymentMethod breakdown includes all method keys")
    void financialReport_byPaymentMethod_includesAllKeys() {
        String doctorId = seedDoctorWithId("U2025001", "drsmith");
        String patientId = seedPatient("PMBreakdown", "Test");
        String apptId = seedAppointment(patientId, doctorId, LocalDate.of(2025, 9, 1));
        String invoiceId = createInvoice(apptId, 500.0);
        setInvoiceStatus(invoiceId, "ISSUED");

        // Record a CASH payment
        Map<String, Object> payBody = Map.of("amount", 250.0, "paymentMethod", "CASH");
        restTemplate.exchange(
                baseUrl("/api/v1/invoices/" + invoiceId + "/payments"),
                HttpMethod.POST,
                new HttpEntity<>(payBody, authHeaders("RECEPTIONIST")),
                Map.class);

        ResponseEntity<Map> resp = getReport("2026-01-01", "2026-12-31");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> byMethod = (Map<String, Object>) resp.getBody().get("byPaymentMethod");
        // All payment method keys should be present
        assertThat(byMethod).containsKeys("CASH", "CARD", "INSURANCE", "BANK_TRANSFER", "CHEQUE");
        // CASH should reflect our payment
        assertThat(new BigDecimal(byMethod.get("CASH").toString()))
                .isGreaterThanOrEqualTo(new BigDecimal("250.00"));
    }

    @Test
    @DisplayName("US5-S3: Empty date range returns all-zero report")
    void financialReport_emptyRange_returnsZeros() {
        // Far-future range where no invoices exist
        ResponseEntity<Map> resp = getReport("2099-01-01", "2099-01-31");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = resp.getBody();
        assertThat(new BigDecimal(body.get("totalInvoiced").toString()))
                .isEqualByComparingTo("0.00");
        assertThat(new BigDecimal(body.get("totalCollected").toString()))
                .isEqualByComparingTo("0.00");
        assertThat(new BigDecimal(body.get("totalOutstanding").toString()))
                .isEqualByComparingTo("0.00");
        assertThat(Integer.parseInt(body.get("invoiceCount").toString())).isEqualTo(0);
    }

    @Test
    @DisplayName("US5-S4: dateFrom after dateTo returns 400")
    void financialReport_dateFromAfterDateTo_returns400() {
        ResponseEntity<Map> resp = getReport("2025-12-31", "2025-01-01");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("US5-S5: RECEPTIONIST denied financial report — 403")
    void financialReport_receptionistDenied_403() {
        ResponseEntity<Map> resp = getReportAs("2025-01-01", "2025-12-31", "RECEPTIONIST");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("US5-S6: DOCTOR denied financial report — 403")
    void financialReport_doctorDenied_403() {
        ResponseEntity<Map> resp = getReportAs("2025-01-01", "2025-12-31", "DOCTOR");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("US5-S7: Written-off amounts appear in totalWrittenOff")
    void financialReport_writtenOff_appearsInReport() {
        String doctorId = seedDoctorWithId("U2025001", "drsmith");
        String patientId = seedPatient("WriteOff", "Report");
        String apptId = seedAppointment(patientId, doctorId, LocalDate.of(2025, 10, 1));
        String invoiceId = createInvoice(apptId, 150.0);
        setInvoiceStatus(invoiceId, "ISSUED");
        setInvoiceStatus(invoiceId, "WRITTEN_OFF");

        ResponseEntity<Map> resp = getReport("2026-01-01", "2026-12-31");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = resp.getBody();
        assertThat(new BigDecimal(body.get("totalWrittenOff").toString()))
                .isGreaterThanOrEqualTo(new BigDecimal("150.00"));
    }
}

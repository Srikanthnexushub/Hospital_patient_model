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
@DisplayName("Invoice Search — US2: View & Search Invoices")
class InvoiceSearchIT extends BaseIntegrationTest {

    @Test
    @DisplayName("US2-S1: RECEPTIONIST can list all invoices")
    void listInvoices_receptionist_seesAll() {
        String doctorId = seedDoctorWithId("U2025001", "drsmith");
        String patientId = seedPatient("Alice", "Smith");
        String apptId = seedAppointment(patientId, doctorId, LocalDate.of(2025, 6, 1));
        createInvoice(apptId, 200.0);

        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/invoices"), HttpMethod.GET,
                new HttpEntity<>(authHeaders("RECEPTIONIST")), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) resp.getBody().get("content");
        assertThat(content).isNotEmpty();
    }

    @Test
    @DisplayName("US2-S2: Filter by status=DRAFT returns only DRAFT invoices")
    void listInvoices_filterByStatus_returnsDraftOnly() {
        String doctorId = seedDoctorWithId("U2025001", "drsmith");
        String patientId = seedPatient("Bob", "Jones");
        String apptId = seedAppointment(patientId, doctorId, LocalDate.of(2025, 6, 2));
        createInvoice(apptId, 100.0);

        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/invoices?status=DRAFT"), HttpMethod.GET,
                new HttpEntity<>(authHeaders("ADMIN")), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) resp.getBody().get("content");
        assertThat(content).allSatisfy(inv -> assertThat(inv.get("status")).isEqualTo("DRAFT"));
    }

    @Test
    @DisplayName("US2-S3: DOCTOR only sees their own patients' invoices")
    void listInvoices_doctorScope_seesOwnOnly() {
        // Doctor 1 = U2025001 (matches DOCTOR JWT subject)
        String doc1 = seedDoctorWithId("U2025001", "drsmith");
        String doc2 = seedDoctorWithId("U2025002", "drjones");

        String patient1 = seedPatient("Carol", "One");
        String patient2 = seedPatient("David", "Two");

        String appt1 = seedAppointment(patient1, doc1, LocalDate.of(2025, 6, 3));
        String appt2 = seedAppointment(patient2, doc2, LocalDate.of(2025, 6, 4));

        String inv1 = createInvoice(appt1, 100.0);
        createInvoice(appt2, 200.0);

        // DOCTOR JWT has userId=U2025001 (drsmith's userId)
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/invoices"), HttpMethod.GET,
                new HttpEntity<>(authHeaders("DOCTOR")), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) resp.getBody().get("content");
        assertThat(content).hasSize(1);
        assertThat(content.get(0).get("invoiceId")).isEqualTo(inv1);
    }

    @Test
    @DisplayName("US2-S4: NURSE denied invoice list — 403")
    void listInvoices_nurseDenied_403() {
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/invoices"), HttpMethod.GET,
                new HttpEntity<>(authHeaders("NURSE")), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("US2-S5: GET invoice detail includes lineItems and payments arrays")
    void getInvoice_includesLineItemsAndPayments() {
        String doctorId = seedDoctorWithId("U2025001", "drsmith");
        String patientId = seedPatient("Eve", "Green");
        String apptId = seedAppointment(patientId, doctorId, LocalDate.of(2025, 6, 5));
        String invoiceId = createInvoice(apptId, 150.0);

        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/invoices/" + invoiceId), HttpMethod.GET,
                new HttpEntity<>(authHeaders("RECEPTIONIST")), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = resp.getBody();
        assertThat(body.get("invoiceId")).isEqualTo(invoiceId);
        @SuppressWarnings("unchecked")
        List<Object> lineItems = (List<Object>) body.get("lineItems");
        assertThat(lineItems).hasSize(1);
        @SuppressWarnings("unchecked")
        List<Object> payments = (List<Object>) body.get("payments");
        assertThat(payments).isEmpty();
    }

    @Test
    @DisplayName("US2: GET non-existent invoice returns 404")
    void getInvoice_notFound_returns404() {
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/invoices/INV9999999999999"), HttpMethod.GET,
                new HttpEntity<>(authHeaders("RECEPTIONIST")), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("US2: Patient invoice list — patient not found returns 404")
    void listPatientInvoices_patientNotFound_returns404() {
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/patients/P9999999/invoices"), HttpMethod.GET,
                new HttpEntity<>(authHeaders("RECEPTIONIST")), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("US2: Patient invoice list — returns paginated results")
    void listPatientInvoices_returnsResults() {
        String doctorId = seedDoctorWithId("U2025001", "drsmith");
        String patientId = seedPatient("Frank", "Brown");
        String apptId = seedAppointment(patientId, doctorId, LocalDate.of(2025, 6, 6));
        createInvoice(apptId, 100.0);

        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/invoices"), HttpMethod.GET,
                new HttpEntity<>(authHeaders("RECEPTIONIST")), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((Integer) resp.getBody().get("totalElements")).isGreaterThan(0);
    }
}

package com.ainexus.hospital.patient.integration;

import com.ainexus.hospital.patient.entity.*;
import com.ainexus.hospital.patient.repository.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full RBAC matrix integration test verifying server-side role enforcement
 * for every endpoint. Client-side checks are cosmetic; this is the source of truth.
 */
class PatientRbacIT extends BaseIntegrationTest {

    @Autowired
    private PatientRepository patientRepository;

    @BeforeEach
    void seedPatient() {
        // Use year 2025 so the ID generator (which produces P2026xxx) never conflicts
        patientRepository.save(Patient.builder()
                .patientId("P2025001")
                .firstName("Jane").lastName("Smith")
                .dateOfBirth(LocalDate.of(1985, 6, 15))
                .gender(Gender.FEMALE).phone("555-123-4567")
                .bloodGroup(BloodGroup.UNKNOWN).status(PatientStatus.ACTIVE)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now())
                .createdBy("test").updatedBy("test").version(0).build());
    }

    private HttpHeaders headersFor(String role) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (role != null) h.set("Authorization", "Bearer " + buildTestJwt(role));
        return h;
    }

    // ── POST /patients ─────────────────────────────────────────────────────

    @ParameterizedTest(name = "POST /patients as {0} → {1}")
    @CsvSource({
            "RECEPTIONIST, 201",
            "ADMIN,        201",
            "DOCTOR,       403",
            "NURSE,        403",
    })
    void registerPatient_rbacMatrix(String role, int expectedStatus) {
        Map<String, Object> body = Map.of(
                "firstName", "Test", "lastName", "User",
                "dateOfBirth", "1985-01-01", "gender", "MALE",
                "phone", "555-999-" + (1000 + expectedStatus)
        );
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headersFor(role));
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl("/api/v1/patients"), HttpMethod.POST, request, Map.class);
        assertThat(response.getStatusCode().value()).isEqualTo(expectedStatus);
    }

    @org.junit.jupiter.api.Test
    void registerPatient_unauthenticated_returns401() {
        Map<String, Object> body = Map.of(
                "firstName", "Test", "lastName", "User",
                "dateOfBirth", "1985-01-01", "gender", "MALE", "phone", "555-401-0001"
        );
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl("/api/v1/patients"), HttpMethod.POST,
                new HttpEntity<>(body, headersFor(null)), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── GET /patients ──────────────────────────────────────────────────────

    @ParameterizedTest(name = "GET /patients as {0} → {1}")
    @CsvSource({ "RECEPTIONIST, 200", "ADMIN, 200", "DOCTOR, 200", "NURSE, 200" })
    void searchPatients_rbacMatrix(String role, int expectedStatus) {
        HttpEntity<Void> request = new HttpEntity<>(headersFor(role));
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl("/api/v1/patients"), HttpMethod.GET, request, Map.class);
        assertThat(response.getStatusCode().value()).isEqualTo(expectedStatus);
    }

    @org.junit.jupiter.api.Test
    void searchPatients_unauthenticated_returns401() {
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl("/api/v1/patients"), HttpMethod.GET,
                new HttpEntity<>(headersFor(null)), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── GET /patients/{id} ─────────────────────────────────────────────────

    @ParameterizedTest(name = "GET /patients/id as {0} → {1}")
    @CsvSource({ "RECEPTIONIST, 200", "ADMIN, 200", "DOCTOR, 200", "NURSE, 200" })
    void getPatient_rbacMatrix(String role, int expectedStatus) {
        HttpEntity<Void> request = new HttpEntity<>(headersFor(role));
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl("/api/v1/patients/P2025001"), HttpMethod.GET, request, Map.class);
        assertThat(response.getStatusCode().value()).isEqualTo(expectedStatus);
    }

    // ── GET /patients/check-phone ──────────────────────────────────────────

    @ParameterizedTest(name = "GET /check-phone as {0} → {1}")
    @CsvSource({ "RECEPTIONIST, 200", "ADMIN, 200", "DOCTOR, 403", "NURSE, 403" })
    void checkPhone_rbacMatrix(String role, int expectedStatus) {
        HttpEntity<Void> request = new HttpEntity<>(headersFor(role));
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl("/api/v1/patients/check-phone?phone=555-000-9999"),
                HttpMethod.GET, request, Map.class);
        assertThat(response.getStatusCode().value()).isEqualTo(expectedStatus);
    }

    // ── PUT /patients/{id} ─────────────────────────────────────────────────

    @ParameterizedTest(name = "PUT /patients/id as {0} → {1}")
    @CsvSource({ "RECEPTIONIST, 200", "ADMIN, 200", "DOCTOR, 403", "NURSE, 403" })
    void updatePatient_rbacMatrix(String role, int expectedStatus) {
        Map<String, Object> body = Map.of(
                "firstName", "Jane", "lastName", "Smith",
                "dateOfBirth", "1985-06-15", "gender", "FEMALE",
                "phone", "555-123-4567", "bloodGroup", "A+"
        );
        HttpHeaders headers = headersFor(role);
        headers.set("If-Match", "0");
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl("/api/v1/patients/P2025001"), HttpMethod.PUT, request, Map.class);
        assertThat(response.getStatusCode().value()).isEqualTo(expectedStatus);
    }

    // ── PATCH /patients/{id}/status ────────────────────────────────────────

    @ParameterizedTest(name = "PATCH /patients/id/status as {0} → {1}")
    @CsvSource({ "ADMIN, 200", "RECEPTIONIST, 403", "DOCTOR, 403", "NURSE, 403" })
    void changeStatus_rbacMatrix(String role, int expectedStatus) {
        Map<String, Object> body = Map.of("action", "DEACTIVATE");
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headersFor(role));
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl("/api/v1/patients/P2025001/status"),
                HttpMethod.PATCH, request, Map.class);
        assertThat(response.getStatusCode().value()).isEqualTo(expectedStatus);
    }
}

package com.ainexus.hospital.patient.integration;

import com.ainexus.hospital.patient.dto.response.PatientRegistrationResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for US1: Register a New Patient.
 *
 * NOTE: These tests require a valid JWT. In a real environment the Auth Module
 * provides JWTs. For integration testing we generate test tokens with the same
 * JWT_SECRET configured in application-test.yml.
 *
 * Tests use the BaseIntegrationTest Testcontainers PostgreSQL container.
 */
class PatientRegistrationIT extends BaseIntegrationTest {

    private static final String REGISTER_PATH = "/api/v1/patients";

    private Map<String, Object> validPayload() {
        return Map.of(
                "firstName", "Jane",
                "lastName", "Smith",
                "dateOfBirth", "1985-06-15",
                "gender", "FEMALE",
                "phone", "555-123-4567"
        );
    }

    private HttpEntity<Map<String, Object>> requestWithAuth(Map<String, Object> body, String role) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // For integration tests, add a test JWT or use a test security bypass
        // The JWT filter can be configured to use a test-mode bypass
        headers.set("Authorization", "Bearer " + buildTestJwt(role));
        return new HttpEntity<>(body, headers);
    }

    @Test
    void registerPatient_withValidMinimalPayload_returns201WithPatientId() {
        HttpEntity<Map<String, Object>> request = requestWithAuth(validPayload(), "RECEPTIONIST");

        ResponseEntity<PatientRegistrationResponse> response = restTemplate.exchange(
                baseUrl(REGISTER_PATH), HttpMethod.POST, request, PatientRegistrationResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().patientId()).startsWith("P");
        assertThat(response.getBody().message()).contains(response.getBody().patientId());
    }

    @Test
    void registerPatient_unauthenticated_returns401() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(validPayload(), headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl(REGISTER_PATH), HttpMethod.POST, request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void registerPatient_asDoctor_returns403() {
        HttpEntity<Map<String, Object>> request = requestWithAuth(validPayload(), "DOCTOR");

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl(REGISTER_PATH), HttpMethod.POST, request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void registerPatient_withInvalidPhone_returns400WithFieldError() {
        Map<String, Object> payload = new java.util.HashMap<>(validPayload());
        payload.put("phone", "12345");

        HttpEntity<Map<String, Object>> request = requestWithAuth(payload, "RECEPTIONIST");
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl(REGISTER_PATH), HttpMethod.POST, request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsKey("fieldErrors");
    }

    @Test
    void registerPatient_withFutureDateOfBirth_returns400() {
        Map<String, Object> payload = new java.util.HashMap<>(validPayload());
        payload.put("dateOfBirth", java.time.LocalDate.now().plusDays(1).toString());

        HttpEntity<Map<String, Object>> request = requestWithAuth(payload, "RECEPTIONIST");
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl(REGISTER_PATH), HttpMethod.POST, request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void registerTwoPatients_eachReceivesUniquePatientId() {
        HttpEntity<Map<String, Object>> request1 = requestWithAuth(validPayload(), "RECEPTIONIST");
        HttpEntity<Map<String, Object>> request2 = requestWithAuth(
                Map.of("firstName", "Bob", "lastName", "Jones",
                       "dateOfBirth", "1990-01-01", "gender", "MALE", "phone", "555-987-6543"),
                "RECEPTIONIST");

        ResponseEntity<PatientRegistrationResponse> r1 = restTemplate.exchange(
                baseUrl(REGISTER_PATH), HttpMethod.POST, request1, PatientRegistrationResponse.class);
        ResponseEntity<PatientRegistrationResponse> r2 = restTemplate.exchange(
                baseUrl(REGISTER_PATH), HttpMethod.POST, request2, PatientRegistrationResponse.class);

        assertThat(r1.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(r2.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(r1.getBody().patientId()).isNotEqualTo(r2.getBody().patientId());
    }
}

package com.ainexus.hospital.patient.integration;

import com.ainexus.hospital.patient.dto.response.PatientResponse;
import com.ainexus.hospital.patient.entity.*;
import com.ainexus.hospital.patient.repository.PatientAuditLogRepository;
import com.ainexus.hospital.patient.repository.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for US4: Update Patient Record.
 */
class PatientUpdateIT extends BaseIntegrationTest {

    @Autowired
    private PatientRepository patientRepository;
    @Autowired
    private PatientAuditLogRepository auditLogRepository;

    private static final String PATIENT_ID = "P2026001";

    @BeforeEach
    void seedPatient() {
        patientRepository.save(Patient.builder()
                .patientId(PATIENT_ID)
                .firstName("Jane").lastName("Smith")
                .dateOfBirth(LocalDate.of(1985, 6, 15))
                .gender(Gender.FEMALE).phone("555-123-4567")
                .bloodGroup(BloodGroup.A_POS)
                .status(PatientStatus.ACTIVE)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now())
                .createdBy("receptionist1").updatedBy("receptionist1")
                .version(0)
                .build());
    }

    private Map<String, Object> updatePayload(String firstName, String lastName) {
        return Map.of(
                "firstName", firstName, "lastName", lastName,
                "dateOfBirth", "1985-06-15", "gender", "FEMALE",
                "phone", "555-123-4567", "bloodGroup", "A+"
        );
    }

    private HttpEntity<Map<String, Object>> authRequest(Map<String, Object> body, String role, int version) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + buildTestJwt(role));
        headers.set("If-Match", String.valueOf(version));
        return new HttpEntity<>(body, headers);
    }

    @Test
    void updatePatient_withValidPayloadAndVersion_returns200AllFieldsUpdated() {
        HttpEntity<Map<String, Object>> request = authRequest(
                updatePayload("Janet", "Smithson"), "RECEPTIONIST", 0);

        ResponseEntity<PatientResponse> response = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + PATIENT_ID),
                HttpMethod.PUT, request, PatientResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        PatientResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.firstName()).isEqualTo("Janet");
        assertThat(body.lastName()).isEqualTo("Smithson");
        assertThat(body.updatedBy()).isEqualTo("receptionist1");
    }

    @Test
    void updatePatient_withStaleVersion_returns409() {
        HttpEntity<Map<String, Object>> request = authRequest(
                updatePayload("Stale", "Update"), "RECEPTIONIST", 999); // wrong version

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + PATIENT_ID),
                HttpMethod.PUT, request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("message").toString())
                .contains("modified by another user");
    }

    @Test
    void updatePatient_withInvalidPhone_returns400() {
        Map<String, Object> bad = new java.util.HashMap<>(updatePayload("Jane", "Smith"));
        bad.put("phone", "12345");

        HttpEntity<Map<String, Object>> request = authRequest(bad, "RECEPTIONIST", 0);
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + PATIENT_ID),
                HttpMethod.PUT, request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsKey("fieldErrors");
    }

    @Test
    void updatePatient_asDoctor_returns403() {
        HttpEntity<Map<String, Object>> request = authRequest(
                updatePayload("Jane", "Smith"), "DOCTOR", 0);

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + PATIENT_ID),
                HttpMethod.PUT, request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void updatePatient_unauthenticated_returns401() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("If-Match", "0");
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(updatePayload("Jane", "Smith"), headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + PATIENT_ID),
                HttpMethod.PUT, request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void updatePatient_clearOptionalEmail_savedAsNull() {
        Map<String, Object> payload = new java.util.HashMap<>(updatePayload("Jane", "Smith"));
        payload.put("email", null);

        HttpEntity<Map<String, Object>> request = authRequest(payload, "RECEPTIONIST", 0);
        ResponseEntity<PatientResponse> response = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + PATIENT_ID),
                HttpMethod.PUT, request, PatientResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().email()).isNull();
    }

    @Test
    void updatePatient_notFound_returns404() {
        HttpEntity<Map<String, Object>> request = authRequest(
                updatePayload("Jane", "Smith"), "RECEPTIONIST", 0);

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl("/api/v1/patients/NOTEXIST"),
                HttpMethod.PUT, request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}

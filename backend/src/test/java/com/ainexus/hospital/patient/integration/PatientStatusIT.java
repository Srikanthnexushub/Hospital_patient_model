package com.ainexus.hospital.patient.integration;

import com.ainexus.hospital.patient.dto.response.PatientStatusChangeResponse;
import com.ainexus.hospital.patient.entity.*;
import com.ainexus.hospital.patient.repository.PatientAuditLogRepository;
import com.ainexus.hospital.patient.repository.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for US5: Patient Status Management.
 */
class PatientStatusIT extends BaseIntegrationTest {

    @Autowired private PatientRepository patientRepository;
    @Autowired private PatientAuditLogRepository auditLogRepository;

    private static final String ACTIVE_ID   = "P2026001";
    private static final String INACTIVE_ID = "P2026002";

    @BeforeEach
    void seedPatients() {
        patientRepository.save(Patient.builder()
                .patientId(ACTIVE_ID)
                .firstName("Active").lastName("Patient")
                .dateOfBirth(LocalDate.of(1985, 1, 1))
                .gender(Gender.MALE).phone("555-001-0001")
                .bloodGroup(BloodGroup.UNKNOWN).status(PatientStatus.ACTIVE)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now())
                .createdBy("test").updatedBy("test").version(0).build());

        patientRepository.save(Patient.builder()
                .patientId(INACTIVE_ID)
                .firstName("Inactive").lastName("Patient")
                .dateOfBirth(LocalDate.of(1985, 1, 1))
                .gender(Gender.FEMALE).phone("555-002-0001")
                .bloodGroup(BloodGroup.UNKNOWN).status(PatientStatus.INACTIVE)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now())
                .createdBy("test").updatedBy("test").version(0).build());
    }

    private HttpEntity<Map<String, Object>> authRequest(String role, String action) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + buildTestJwt(role));
        return new HttpEntity<>(Map.of("action", action), headers);
    }

    @Test
    void deactivateActivePatient_asAdmin_returns200WithInactiveStatus() {
        ResponseEntity<PatientStatusChangeResponse> response = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + ACTIVE_ID + "/status"),
                HttpMethod.PATCH, authRequest("ADMIN", "DEACTIVATE"),
                PatientStatusChangeResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        PatientStatusChangeResponse body = response.getBody();
        assertThat(body.status()).isEqualTo(PatientStatus.INACTIVE);
        assertThat(body.message()).contains("deactivated");

        // Verify audit log
        List<PatientAuditLog> logs = auditLogRepository.findAll()
                .stream().filter(l -> l.getPatientId().equals(ACTIVE_ID)).toList();
        assertThat(logs).anyMatch(l -> "DEACTIVATE".equals(l.getOperation()));
    }

    @Test
    void activateInactivePatient_asAdmin_returns200WithActiveStatus() {
        ResponseEntity<PatientStatusChangeResponse> response = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + INACTIVE_ID + "/status"),
                HttpMethod.PATCH, authRequest("ADMIN", "ACTIVATE"),
                PatientStatusChangeResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        PatientStatusChangeResponse body = response.getBody();
        assertThat(body.status()).isEqualTo(PatientStatus.ACTIVE);
        assertThat(body.message()).contains("activated");

        List<PatientAuditLog> logs = auditLogRepository.findAll()
                .stream().filter(l -> l.getPatientId().equals(INACTIVE_ID)).toList();
        assertThat(logs).anyMatch(l -> "ACTIVATE".equals(l.getOperation()));
    }

    @Test
    void deactivateAlreadyInactivePatient_returns409() {
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + INACTIVE_ID + "/status"),
                HttpMethod.PATCH, authRequest("ADMIN", "DEACTIVATE"),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("message").toString()).contains("already inactive");
    }

    @Test
    void activateAlreadyActivePatient_returns409() {
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + ACTIVE_ID + "/status"),
                HttpMethod.PATCH, authRequest("ADMIN", "ACTIVATE"),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("message").toString()).contains("already active");
    }

    @Test
    void changeStatus_asReceptionist_returns403() {
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + ACTIVE_ID + "/status"),
                HttpMethod.PATCH, authRequest("RECEPTIONIST", "DEACTIVATE"),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void changeStatus_asDoctor_returns403() {
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + ACTIVE_ID + "/status"),
                HttpMethod.PATCH, authRequest("DOCTOR", "DEACTIVATE"),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void changeStatus_asNurse_returns403() {
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + ACTIVE_ID + "/status"),
                HttpMethod.PATCH, authRequest("NURSE", "DEACTIVATE"),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void changeStatus_unauthenticated_returns401() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(Map.of("action", "DEACTIVATE"), headers);
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + ACTIVE_ID + "/status"),
                HttpMethod.PATCH, request, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void changeStatus_patientNotFound_returns404() {
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl("/api/v1/patients/NOTEXIST/status"),
                HttpMethod.PATCH, authRequest("ADMIN", "DEACTIVATE"),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}

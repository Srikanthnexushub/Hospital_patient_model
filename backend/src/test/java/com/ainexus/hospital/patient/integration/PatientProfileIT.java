package com.ainexus.hospital.patient.integration;

import com.ainexus.hospital.patient.dto.response.PatientResponse;
import com.ainexus.hospital.patient.entity.*;
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
 * Integration tests for US3: View Patient Profile.
 */
class PatientProfileIT extends BaseIntegrationTest {

    @Autowired
    private PatientRepository patientRepository;

    private Patient testPatient;

    @BeforeEach
    void seedPatient() {
        testPatient = patientRepository.save(Patient.builder()
                .patientId("P2026001")
                .firstName("Jane").lastName("Smith")
                .dateOfBirth(LocalDate.of(1990, 6, 15))
                .gender(Gender.FEMALE)
                .phone("555-123-4567")
                .email("jane@example.com")
                .address("123 Main St").city("Springfield").state("IL").zipCode("62701")
                .emergencyContactName("John Smith")
                .emergencyContactPhone("555-987-6543")
                .emergencyContactRelationship("Spouse")
                .knownAllergies("Penicillin")
                .chronicConditions("Diabetes")
                .bloodGroup(BloodGroup.A_POS)
                .status(PatientStatus.ACTIVE)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now())
                .createdBy("receptionist1").updatedBy("receptionist1")
                .version(0)
                .build());
    }

    private HttpEntity<Void> authRequest(String role) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + buildTestJwt(role));
        return new HttpEntity<>(headers);
    }

    @Test
    void getPatient_withValidId_returnsFullProfile() {
        ResponseEntity<PatientResponse> response = restTemplate.exchange(
                baseUrl("/api/v1/patients/P2026001"),
                HttpMethod.GET, authRequest("RECEPTIONIST"),
                PatientResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        PatientResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.patientId()).isEqualTo("P2026001");
        assertThat(body.firstName()).isEqualTo("Jane");
        assertThat(body.lastName()).isEqualTo("Smith");
        assertThat(body.gender()).isEqualTo(Gender.FEMALE);
        assertThat(body.bloodGroup()).isEqualTo(BloodGroup.A_POS);
        assertThat(body.phone()).isEqualTo("555-123-4567");
        assertThat(body.email()).isEqualTo("jane@example.com");
        assertThat(body.address()).isEqualTo("123 Main St");
        assertThat(body.emergencyContactName()).isEqualTo("John Smith");
        assertThat(body.knownAllergies()).isEqualTo("Penicillin");
        assertThat(body.chronicConditions()).isEqualTo("Diabetes");
        assertThat(body.status()).isEqualTo(PatientStatus.ACTIVE);
        assertThat(body.createdBy()).isEqualTo("receptionist1");
        assertThat(body.version()).isEqualTo(0);
        assertThat(body.age()).isGreaterThan(0);
    }

    @Test
    void getPatient_withUnknownId_returns404() {
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl("/api/v1/patients/NOTEXIST"),
                HttpMethod.GET, authRequest("RECEPTIONIST"),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsKey("message");
    }

    @Test
    void getPatient_allFourRoles_return200() {
        for (String role : List.of("RECEPTIONIST", "ADMIN", "DOCTOR", "NURSE")) {
            ResponseEntity<PatientResponse> response = restTemplate.exchange(
                    baseUrl("/api/v1/patients/P2026001"),
                    HttpMethod.GET, authRequest(role),
                    PatientResponse.class);
            assertThat(response.getStatusCode())
                    .as("Role %s should get 200", role)
                    .isEqualTo(HttpStatus.OK);
        }
    }

    @Test
    void getPatient_unauthenticated_returns401() {
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl("/api/v1/patients/P2026001"),
                HttpMethod.GET, new HttpEntity<>(new HttpHeaders()),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void getPatient_optionalNullFieldsReturnedAsNull() {
        Patient noExtras = patientRepository.save(Patient.builder()
                .patientId("P2026002")
                .firstName("Bob").lastName("Jones")
                .dateOfBirth(LocalDate.of(1985, 1, 1))
                .gender(Gender.MALE).phone("555-111-2222")
                .bloodGroup(BloodGroup.UNKNOWN)
                .status(PatientStatus.ACTIVE)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now())
                .createdBy("test").updatedBy("test")
                .version(0)
                .build());

        ResponseEntity<PatientResponse> response = restTemplate.exchange(
                baseUrl("/api/v1/patients/P2026002"),
                HttpMethod.GET, authRequest("DOCTOR"),
                PatientResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        PatientResponse body = response.getBody();
        assertThat(body.email()).isNull();
        assertThat(body.emergencyContactName()).isNull();
    }
}

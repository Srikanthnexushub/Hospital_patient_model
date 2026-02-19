package com.ainexus.hospital.patient.integration;

import com.ainexus.hospital.patient.dto.response.PagedResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import com.ainexus.hospital.patient.entity.*;
import com.ainexus.hospital.patient.repository.PatientRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for US2: Search and List Patients.
 */
class PatientSearchIT extends BaseIntegrationTest {

    @Autowired
    private PatientRepository patientRepository;

    @BeforeEach
    void seedPatients() {
        // 25 patients: 20 ACTIVE females named "Smith" + 4 ACTIVE males + 1 INACTIVE
        List<Patient> patients = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            patients.add(Patient.builder()
                    .patientId(String.format("P2026%03d", i))
                    .firstName("Jane").lastName("Smith")
                    .dateOfBirth(LocalDate.of(1985, 1, 1))
                    .gender(Gender.FEMALE)
                    .phone(String.format("555-%03d-0001", i))
                    .status(PatientStatus.ACTIVE)
                    .bloodGroup(BloodGroup.A_POS)
                    .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now())
                    .createdBy("test").updatedBy("test")
                    .version(0)
                    .build());
        }
        for (int i = 21; i <= 24; i++) {
            patients.add(Patient.builder()
                    .patientId(String.format("P2026%03d", i))
                    .firstName("Bob").lastName("Jones")
                    .dateOfBirth(LocalDate.of(1990, 5, 10))
                    .gender(Gender.MALE)
                    .phone(String.format("555-%03d-0002", i))
                    .status(PatientStatus.ACTIVE)
                    .bloodGroup(BloodGroup.B_POS)
                    .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now())
                    .createdBy("test").updatedBy("test")
                    .version(0)
                    .build());
        }
        // One inactive patient
        patients.add(Patient.builder()
                .patientId("P2026025")
                .firstName("Inactive").lastName("Patient")
                .dateOfBirth(LocalDate.of(1970, 3, 3))
                .gender(Gender.OTHER)
                .phone("555-025-0001")
                .status(PatientStatus.INACTIVE)
                .bloodGroup(BloodGroup.UNKNOWN)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now())
                .createdBy("test").updatedBy("test")
                .version(0)
                .build());
        patientRepository.saveAll(patients);
    }

    private HttpEntity<Void> authRequest(String role) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + buildTestJwt(role));
        return new HttpEntity<>(headers);
    }

    @Test
    void searchPatients_defaultPage0Size20_returnsFirst20WithPagination() {
        ResponseEntity<PagedResponse> response = restTemplate.exchange(
                baseUrl("/api/v1/patients?status=ACTIVE"),
                HttpMethod.GET, authRequest("RECEPTIONIST"),
                PagedResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().first()).isTrue();
        assertThat(response.getBody().last()).isFalse();
        assertThat(response.getBody().totalElements()).isEqualTo(24); // 20 Smith + 4 Jones ACTIVE
    }

    @Test
    void searchPatients_page1_returnsRemainingPatients() {
        ResponseEntity<PagedResponse> response = restTemplate.exchange(
                baseUrl("/api/v1/patients?status=ACTIVE&page=1&size=20"),
                HttpMethod.GET, authRequest("RECEPTIONIST"),
                PagedResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().last()).isTrue();
        assertThat(response.getBody().page()).isEqualTo(1);
    }

    @Test
    void searchPatients_querySmith_returnsOnlySmithPatients() {
        ResponseEntity<PagedResponse> response = restTemplate.exchange(
                baseUrl("/api/v1/patients?query=smith&status=ACTIVE"),
                HttpMethod.GET, authRequest("RECEPTIONIST"),
                PagedResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().totalElements()).isEqualTo(20);
    }

    @Test
    void searchPatients_statusAll_includesInactiveCount() {
        ResponseEntity<PagedResponse> response = restTemplate.exchange(
                baseUrl("/api/v1/patients?status=ALL"),
                HttpMethod.GET, authRequest("ADMIN"),
                PagedResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().totalElements()).isEqualTo(25); // all including inactive
    }

    @Test
    void searchPatients_genderFemale_returnsOnlyFemalePatients() {
        ResponseEntity<PagedResponse> response = restTemplate.exchange(
                baseUrl("/api/v1/patients?gender=FEMALE&status=ALL"),
                HttpMethod.GET, authRequest("DOCTOR"),
                PagedResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().totalElements()).isEqualTo(20);
    }

    @Test
    void searchPatients_queryPatientIdPrefix_returnsMatchingPatient() {
        ResponseEntity<PagedResponse> response = restTemplate.exchange(
                baseUrl("/api/v1/patients?query=P2026001&status=ALL"),
                HttpMethod.GET, authRequest("NURSE"),
                PagedResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().totalElements()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void searchPatients_queryXyz_returnsEmptyResultsNoError() {
        ResponseEntity<PagedResponse> response = restTemplate.exchange(
                baseUrl("/api/v1/patients?query=xyznotfound&status=ACTIVE"),
                HttpMethod.GET, authRequest("RECEPTIONIST"),
                PagedResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().totalElements()).isEqualTo(0);
    }

    @Test
    void searchPatients_allFourRoles_return200() {
        for (String role : List.of("RECEPTIONIST", "ADMIN", "DOCTOR", "NURSE")) {
            ResponseEntity<PagedResponse> response = restTemplate.exchange(
                    baseUrl("/api/v1/patients?status=ACTIVE"),
                    HttpMethod.GET, authRequest(role), PagedResponse.class);
            assertThat(response.getStatusCode())
                    .as("Role %s should get 200", role)
                    .isEqualTo(HttpStatus.OK);
        }
    }

    @Test
    void searchPatients_unauthenticated_returns401() {
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl("/api/v1/patients"), HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}

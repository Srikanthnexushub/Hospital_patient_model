package com.ainexus.hospital.patient.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("emr")
@DisplayName("Medications — US3: Medication List")
class MedicationIT extends BaseIntegrationTest {

    private String patientId;

    @BeforeEach
    void setup() {
        patientId = seedPatient("Bob", "Jones");
    }

    private Map<String, Object> validMedicationBody() {
        return Map.of(
                "medicationName", "Lisinopril",
                "dosage", "10mg",
                "frequency", "Once daily",
                "route", "ORAL",
                "startDate", "2026-01-01"
        );
    }

    // ── POST /api/v1/patients/{id}/medications ────────────────────────────

    @Test
    @DisplayName("US3-S1: DOCTOR prescribes medication — 201 with prescribedBy set from context")
    void prescribeMedication_doctorRole_returns201() {
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/medications"),
                HttpMethod.POST,
                new HttpEntity<>(validMedicationBody(), authHeaders("DOCTOR")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody().get("medicationName")).isEqualTo("Lisinopril");
        assertThat(resp.getBody().get("prescribedBy")).isEqualTo("doctor1");
        assertThat(resp.getBody().get("status")).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("US3-S2: NURSE is denied prescribe — 403")
    void prescribeMedication_nurseRole_returns403() {
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/medications"),
                HttpMethod.POST,
                new HttpEntity<>(validMedicationBody(), authHeaders("NURSE")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("US3-S3: End date before start date returns 400")
    void prescribeMedication_endBeforeStart_returns400() {
        Map<String, Object> body = Map.of(
                "medicationName", "Aspirin",
                "dosage", "100mg",
                "frequency", "Once daily",
                "route", "ORAL",
                "startDate", "2026-06-01",
                "endDate", "2026-01-01"
        );

        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/medications"),
                HttpMethod.POST,
                new HttpEntity<>(body, authHeaders("DOCTOR")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── GET /api/v1/patients/{id}/medications ─────────────────────────────

    @Test
    @DisplayName("US3-S4: List active medications — default filter")
    void listMedications_default_returnsActive() {
        restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/medications"),
                HttpMethod.POST,
                new HttpEntity<>(validMedicationBody(), authHeaders("DOCTOR")),
                Map.class);

        ResponseEntity<List> resp = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/medications"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders("NURSE")),
                List.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).hasSize(1);
    }

    // ── PATCH /api/v1/patients/{id}/medications/{medicationId} ────────────

    @Test
    @DisplayName("US3-S5: DOCTOR discontinues medication — status becomes DISCONTINUED")
    void updateMedication_discontinue_returns200() {
        ResponseEntity<Map> created = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/medications"),
                HttpMethod.POST,
                new HttpEntity<>(validMedicationBody(), authHeaders("DOCTOR")),
                Map.class);
        String medId = created.getBody().get("id").toString();

        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/medications/" + medId),
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("status", "DISCONTINUED"), authHeaders("DOCTOR")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("status")).isEqualTo("DISCONTINUED");
    }

    @Test
    @DisplayName("US3-S6: Discontinued medication excluded from active list")
    void listMedications_afterDiscontinue_notInActiveList() {
        ResponseEntity<Map> created = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/medications"),
                HttpMethod.POST,
                new HttpEntity<>(validMedicationBody(), authHeaders("DOCTOR")),
                Map.class);
        String medId = created.getBody().get("id").toString();

        restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/medications/" + medId),
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("status", "DISCONTINUED"), authHeaders("DOCTOR")),
                Map.class);

        ResponseEntity<List> resp = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/medications"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders("DOCTOR")),
                List.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isEmpty();
    }
}

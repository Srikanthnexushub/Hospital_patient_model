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
@DisplayName("Allergies — US4: Allergy Registry")
class AllergyIT extends BaseIntegrationTest {

    private String patientId;

    @BeforeEach
    void setup() {
        patientId = seedPatient("Carol", "White");
    }

    private Map<String, Object> validAllergyBody() {
        return Map.of(
                "substance", "Penicillin",
                "type", "DRUG",
                "severity", "SEVERE",
                "reaction", "Anaphylaxis"
        );
    }

    // ── POST /api/v1/patients/{id}/allergies ──────────────────────────────

    @Test
    @DisplayName("US4-S1: DOCTOR records allergy — 201 with active=true")
    void recordAllergy_doctorRole_returns201() {
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/allergies"),
                HttpMethod.POST,
                new HttpEntity<>(validAllergyBody(), authHeaders("DOCTOR")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody().get("substance")).isEqualTo("Penicillin");
        assertThat(resp.getBody().get("active")).isEqualTo(true);
    }

    @Test
    @DisplayName("US4-S2: NURSE records allergy — 201")
    void recordAllergy_nurseRole_returns201() {
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/allergies"),
                HttpMethod.POST,
                new HttpEntity<>(validAllergyBody(), authHeaders("NURSE")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("US4-S3: RECEPTIONIST is denied create — 403")
    void recordAllergy_receptionistRole_returns403() {
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/allergies"),
                HttpMethod.POST,
                new HttpEntity<>(validAllergyBody(), authHeaders("RECEPTIONIST")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("US4-S4: Missing required field returns 400")
    void recordAllergy_missingReaction_returns400() {
        Map<String, Object> body = Map.of(
                "substance", "Penicillin",
                "type", "DRUG",
                "severity", "SEVERE"
        );

        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/allergies"),
                HttpMethod.POST,
                new HttpEntity<>(body, authHeaders("DOCTOR")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── GET /api/v1/patients/{id}/allergies ───────────────────────────────

    @Test
    @DisplayName("US4-S5: RECEPTIONIST can view allergies — 200")
    void listAllergies_receptionistRole_returns200() {
        restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/allergies"),
                HttpMethod.POST,
                new HttpEntity<>(validAllergyBody(), authHeaders("DOCTOR")),
                Map.class);

        ResponseEntity<List> resp = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/allergies"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders("RECEPTIONIST")),
                List.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).hasSize(1);
    }

    // ── DELETE /api/v1/patients/{id}/allergies/{allergyId} ────────────────

    @Test
    @DisplayName("US4-S6: DOCTOR soft-deletes allergy — 204; no longer in list")
    void deleteAllergy_softDelete_removedFromList() {
        ResponseEntity<Map> created = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/allergies"),
                HttpMethod.POST,
                new HttpEntity<>(validAllergyBody(), authHeaders("DOCTOR")),
                Map.class);
        String allergyId = created.getBody().get("id").toString();

        ResponseEntity<Void> deleteResp = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/allergies/" + allergyId),
                HttpMethod.DELETE,
                new HttpEntity<>(authHeaders("DOCTOR")),
                Void.class);

        assertThat(deleteResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<List> listResp = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/allergies"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders("DOCTOR")),
                List.class);

        assertThat(listResp.getBody()).isEmpty();
    }

    @Test
    @DisplayName("US4-S7: RECEPTIONIST is denied delete — 403")
    void deleteAllergy_receptionistRole_returns403() {
        ResponseEntity<Map> created = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/allergies"),
                HttpMethod.POST,
                new HttpEntity<>(validAllergyBody(), authHeaders("DOCTOR")),
                Map.class);
        String allergyId = created.getBody().get("id").toString();

        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/allergies/" + allergyId),
                HttpMethod.DELETE,
                new HttpEntity<>(authHeaders("RECEPTIONIST")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}

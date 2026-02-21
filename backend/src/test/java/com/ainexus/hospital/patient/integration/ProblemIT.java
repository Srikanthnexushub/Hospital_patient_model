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
@DisplayName("Problems — US2: Problem List")
class ProblemIT extends BaseIntegrationTest {

    private String patientId;

    @BeforeEach
    void setup() {
        patientId = seedPatient("Alice", "Brown");
    }

    private Map<String, Object> validProblemBody() {
        return Map.of(
                "title", "Hypertension",
                "severity", "MODERATE",
                "status", "ACTIVE"
        );
    }

    // ── POST /api/v1/patients/{id}/problems ───────────────────────────────

    @Test
    @DisplayName("US2-S1: DOCTOR creates problem — 201 with title and status")
    void createProblem_doctorRole_returns201() {
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/problems"),
                HttpMethod.POST,
                new HttpEntity<>(validProblemBody(), authHeaders("DOCTOR")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody().get("title")).isEqualTo("Hypertension");
        assertThat(resp.getBody().get("status")).isEqualTo("ACTIVE");
        assertThat(resp.getBody().get("id")).isNotNull();
    }

    @Test
    @DisplayName("US2-S2: NURSE is denied create — 403")
    void createProblem_nurseRole_returns403() {
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/problems"),
                HttpMethod.POST,
                new HttpEntity<>(validProblemBody(), authHeaders("NURSE")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("US2-S3: Missing required field returns 400")
    void createProblem_missingTitle_returns400() {
        Map<String, Object> body = Map.of("severity", "MILD", "status", "ACTIVE");

        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/problems"),
                HttpMethod.POST,
                new HttpEntity<>(body, authHeaders("DOCTOR")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("US2-S4: Patient not found returns 404")
    void createProblem_unknownPatient_returns404() {
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/patients/P9999999/problems"),
                HttpMethod.POST,
                new HttpEntity<>(validProblemBody(), authHeaders("DOCTOR")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── GET /api/v1/patients/{id}/problems ────────────────────────────────

    @Test
    @DisplayName("US2-S5: NURSE can list active problems")
    void listProblems_nurseRole_returns200() {
        // Create a problem first
        restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/problems"),
                HttpMethod.POST,
                new HttpEntity<>(validProblemBody(), authHeaders("DOCTOR")),
                Map.class);

        ResponseEntity<List> resp = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/problems"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders("NURSE")),
                List.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).hasSize(1);
    }

    // ── PATCH /api/v1/patients/{id}/problems/{problemId} ──────────────────

    @Test
    @DisplayName("US2-S6: DOCTOR resolves problem — status becomes RESOLVED")
    void updateProblem_resolve_returns200() {
        ResponseEntity<Map> created = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/problems"),
                HttpMethod.POST,
                new HttpEntity<>(validProblemBody(), authHeaders("DOCTOR")),
                Map.class);
        String problemId = created.getBody().get("id").toString();

        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/problems/" + problemId),
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("status", "RESOLVED"), authHeaders("DOCTOR")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("status")).isEqualTo("RESOLVED");
    }
}

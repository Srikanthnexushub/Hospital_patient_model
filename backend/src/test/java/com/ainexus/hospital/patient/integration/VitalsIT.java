package com.ainexus.hospital.patient.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("emr")
@DisplayName("Vitals — US1: Record & View Vitals")
class VitalsIT extends BaseIntegrationTest {

    private String apptId;
    private String patientId;

    private void setup() {
        seedDoctorWithId("U2025001", "drsmith");
        patientId = seedPatient("Jane", "Smith");
        apptId = bookAppointment(patientId, "U2025001",
                LocalDate.of(2026, 3, 1), LocalTime.of(9, 0));
    }

    private Map<String, Object> validVitalsBody() {
        return Map.of("heartRate", 72, "oxygenSaturation", 98);
    }

    // ── POST /api/v1/appointments/{id}/vitals ──────────────────────────────

    @Test
    @DisplayName("US1-S1: NURSE records vitals — 201 with heartRate and patientId")
    void recordVitals_nurseRole_returns201() {
        setup();

        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/appointments/" + apptId + "/vitals"),
                HttpMethod.POST,
                new HttpEntity<>(validVitalsBody(), authHeaders("NURSE")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody().get("heartRate")).isEqualTo(72);
        assertThat(resp.getBody().get("patientId")).isEqualTo(patientId);
    }

    @Test
    @DisplayName("US1-S2: Re-POST replaces existing vitals (upsert)")
    void recordVitals_secondPost_replacesExisting() {
        setup();

        restTemplate.exchange(
                baseUrl("/api/v1/appointments/" + apptId + "/vitals"),
                HttpMethod.POST,
                new HttpEntity<>(Map.of("heartRate", 72), authHeaders("NURSE")),
                Map.class);

        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/appointments/" + apptId + "/vitals"),
                HttpMethod.POST,
                new HttpEntity<>(Map.of("heartRate", 85), authHeaders("NURSE")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody().get("heartRate")).isEqualTo(85);
    }

    @Test
    @DisplayName("US1-S3: Empty payload (no vitals fields) returns 400")
    void recordVitals_emptyPayload_returns400() {
        setup();

        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/appointments/" + apptId + "/vitals"),
                HttpMethod.POST,
                new HttpEntity<>(Map.of(), authHeaders("NURSE")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("US1-S4: Diastolic exceeding systolic returns 400")
    void recordVitals_bpInvalid_returns400() {
        setup();

        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/appointments/" + apptId + "/vitals"),
                HttpMethod.POST,
                new HttpEntity<>(Map.of("bloodPressureSystolic", 80, "bloodPressureDiastolic", 120),
                        authHeaders("NURSE")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("US1-S5: RECEPTIONIST is denied record access — 403")
    void recordVitals_receptionistRole_returns403() {
        setup();

        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/appointments/" + apptId + "/vitals"),
                HttpMethod.POST,
                new HttpEntity<>(validVitalsBody(), authHeaders("RECEPTIONIST")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── GET /api/v1/appointments/{id}/vitals ──────────────────────────────

    @Test
    @DisplayName("US1-S6: GET vitals by appointment returns recorded data")
    void getVitalsByAppointment_found_returns200() {
        setup();
        restTemplate.exchange(
                baseUrl("/api/v1/appointments/" + apptId + "/vitals"),
                HttpMethod.POST,
                new HttpEntity<>(Map.of("heartRate", 72, "temperature", 36.6),
                        authHeaders("NURSE")),
                Map.class);

        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/appointments/" + apptId + "/vitals"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders("DOCTOR")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("heartRate")).isEqualTo(72);
    }

    @Test
    @DisplayName("US1-S7: GET vitals for appointment with no vitals returns 404")
    void getVitalsByAppointment_noVitals_returns404() {
        setup();

        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/appointments/" + apptId + "/vitals"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders("DOCTOR")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── GET /api/v1/patients/{id}/vitals ──────────────────────────────────

    @Test
    @DisplayName("US1-S8: GET patient vitals history returns paginated results")
    void getVitalsByPatient_returns200WithContent() {
        setup();
        restTemplate.exchange(
                baseUrl("/api/v1/appointments/" + apptId + "/vitals"),
                HttpMethod.POST,
                new HttpEntity<>(Map.of("heartRate", 72), authHeaders("NURSE")),
                Map.class);

        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + patientId + "/vitals"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders("DOCTOR")),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("totalElements")).isEqualTo(1);
    }
}

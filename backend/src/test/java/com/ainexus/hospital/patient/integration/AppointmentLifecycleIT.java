package com.ainexus.hospital.patient.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the full appointment lifecycle:
 * SCHEDULED → CONFIRMED → CHECKED_IN → IN_PROGRESS → COMPLETED
 * and verifies 5 audit log entries are recorded.
 */
@DisplayName("Appointment Lifecycle IT")
class AppointmentLifecycleIT extends BaseIntegrationTest {

    private static final LocalDate TOMORROW = LocalDate.now().plusDays(1);
    private static final LocalTime NINE_AM  = LocalTime.of(9, 0);

    @Test
    @DisplayName("Full lifecycle: SCHEDULED → CONFIRMED → CHECKED_IN → IN_PROGRESS → COMPLETED with 5 audit entries")
    void fullLifecycle_bookToComplete_producesCorrectStatusTransitionsAndAuditLog() {
        // Arrange: seed doctor and patient
        String doctorId  = seedDoctor("doctor1");
        String patientId = seedPatient("John", "Doe");

        // Step 1: Book appointment via RECEPTIONIST → 201 SCHEDULED
        HttpHeaders receptionistHeaders = authHeaders("RECEPTIONIST");
        Map<String, Object> bookBody = Map.of(
                "patientId", patientId,
                "doctorId", doctorId,
                "appointmentDate", TOMORROW.toString(),
                "startTime", NINE_AM.toString(),
                "durationMinutes", 30,
                "type", "GENERAL_CONSULTATION",
                "reason", "Routine check-up"
        );
        ResponseEntity<Map> bookResponse = restTemplate.exchange(
                baseUrl("/api/v1/appointments"),
                HttpMethod.POST,
                new HttpEntity<>(bookBody, receptionistHeaders),
                Map.class);

        assertThat(bookResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat((String) bookResponse.getBody().get("status")).isEqualTo("SCHEDULED");
        String appointmentId = (String) bookResponse.getBody().get("appointmentId");
        assertThat(appointmentId).isNotBlank();

        // Step 2: CONFIRM as RECEPTIONIST → 200 CONFIRMED
        ResponseEntity<Map> confirmResponse = restTemplate.exchange(
                baseUrl("/api/v1/appointments/" + appointmentId + "/status"),
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("action", "CONFIRM"), authHeaders("RECEPTIONIST")),
                Map.class);

        assertThat(confirmResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((String) confirmResponse.getBody().get("newStatus")).isEqualTo("CONFIRMED");

        // Step 3: CHECK_IN as RECEPTIONIST → 200 CHECKED_IN
        ResponseEntity<Map> checkInResponse = restTemplate.exchange(
                baseUrl("/api/v1/appointments/" + appointmentId + "/status"),
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("action", "CHECK_IN"), authHeaders("RECEPTIONIST")),
                Map.class);

        assertThat(checkInResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((String) checkInResponse.getBody().get("newStatus")).isEqualTo("CHECKED_IN");

        // Step 4: START as DOCTOR (userId must match seeded doctor) → 200 IN_PROGRESS
        HttpHeaders doctorHeaders = authHeaders("DOCTOR");
        ResponseEntity<Map> startResponse = restTemplate.exchange(
                baseUrl("/api/v1/appointments/" + appointmentId + "/status"),
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("action", "START"), doctorHeaders),
                Map.class);

        assertThat(startResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((String) startResponse.getBody().get("newStatus")).isEqualTo("IN_PROGRESS");

        // Step 5: COMPLETE as DOCTOR → 200 COMPLETED
        ResponseEntity<Map> completeResponse = restTemplate.exchange(
                baseUrl("/api/v1/appointments/" + appointmentId + "/status"),
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("action", "COMPLETE"), doctorHeaders),
                Map.class);

        assertThat(completeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((String) completeResponse.getBody().get("newStatus")).isEqualTo("COMPLETED");

        // Step 6: GET appointment → 200 status=COMPLETED
        ResponseEntity<Map> getResponse = restTemplate.exchange(
                baseUrl("/api/v1/appointments/" + appointmentId),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders("RECEPTIONIST")),
                Map.class);

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((String) getResponse.getBody().get("status")).isEqualTo("COMPLETED");

        // Step 7: Verify 5 audit log entries (one per status transition)
        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM appointment_audit_log WHERE appointment_id = ?",
                Integer.class,
                appointmentId);
        assertThat(auditCount).isEqualTo(5);
    }
}

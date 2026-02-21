package com.ainexus.hospital.patient.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for appointment update (reschedule) operations.
 *
 * Verifies:
 * - Valid update with correct If-Match version succeeds (200, version increments)
 * - Stale If-Match version is rejected with 409
 * - Updating a COMPLETED appointment is rejected with 409
 */
@DisplayName("Appointment Update IT")
class AppointmentUpdateIT extends BaseIntegrationTest {

    private static final LocalDate TOMORROW = LocalDate.now().plusDays(1);

    /** Builds a PATCH body to reschedule to a new start time. */
    private Map<String, Object> rescheduleBody(String newStartTime) {
        return Map.of(
                "startTime", newStartTime,
                "durationMinutes", 30
        );
    }

    /** Sends a PATCH update with the given If-Match version header. */
    private ResponseEntity<Map> patchAppointment(String appointmentId,
                                                  Map<String, Object> body,
                                                  String role,
                                                  int version) {
        HttpHeaders headers = authHeaders(role);
        headers.set("If-Match", String.valueOf(version));
        return restTemplate.exchange(
                baseUrl("/api/v1/appointments/" + appointmentId),
                HttpMethod.PATCH,
                new HttpEntity<>(body, headers),
                Map.class);
    }

    /** Sends a status transition. */
    private ResponseEntity<Map> transition(String appointmentId,
                                            String action,
                                            HttpHeaders headers) {
        Map<String, Object> body = "CANCEL".equals(action)
                ? Map.of("action", action, "reason", "Test cleanup")
                : Map.of("action", action);
        return restTemplate.exchange(
                baseUrl("/api/v1/appointments/" + appointmentId + "/status"),
                HttpMethod.PATCH,
                new HttpEntity<>(body, headers),
                Map.class);
    }

    @Test
    @DisplayName("PATCH with valid If-Match:0 and new startTime → 200, version becomes 1")
    void updateAppointment_withValidVersion_returns200AndIncrementsVersion() {
        String doctorId      = seedDoctor("doctor1");
        String patientId     = seedPatient("Update", "Valid");
        String appointmentId = bookAppointment(patientId, doctorId, TOMORROW, LocalTime.of(9, 0));

        ResponseEntity<Map> response = patchAppointment(
                appointmentId, rescheduleBody("10:00"), "RECEPTIONIST", 0);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Object version = response.getBody().get("version");
        assertThat(version).isNotNull();
        assertThat(((Number) version).intValue()).isEqualTo(1);
    }

    @Test
    @DisplayName("PATCH with stale If-Match:0 after version already incremented → 409")
    void updateAppointment_withStaleVersion_returns409() {
        String doctorId      = seedDoctor("doctor1");
        String patientId     = seedPatient("Update", "Stale");
        String appointmentId = bookAppointment(patientId, doctorId, TOMORROW, LocalTime.of(9, 30));

        // First update: version 0 → 1
        ResponseEntity<Map> first = patchAppointment(
                appointmentId, rescheduleBody("10:30"), "RECEPTIONIST", 0);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Second update with stale version 0 → 409
        ResponseEntity<Map> second = patchAppointment(
                appointmentId, rescheduleBody("11:00"), "RECEPTIONIST", 0);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("Updating a COMPLETED appointment → 409 (terminal status cannot be modified)")
    void updateAppointment_whenCompleted_returns409() {
        String doctorId      = seedDoctor("doctor1");
        String patientId     = seedPatient("Update", "Completed");
        String appointmentId = bookAppointment(patientId, doctorId, TOMORROW, LocalTime.of(14, 0));

        HttpHeaders receptionistHeaders = authHeaders("RECEPTIONIST");
        HttpHeaders doctorHeaders       = authHeaders("DOCTOR");

        // Drive through the full lifecycle to reach COMPLETED
        assertThat(transition(appointmentId, "CONFIRM",  receptionistHeaders).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(transition(appointmentId, "CHECK_IN", receptionistHeaders).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(transition(appointmentId, "START",    doctorHeaders).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(transition(appointmentId, "COMPLETE", doctorHeaders).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        // Attempt to update the now-COMPLETED appointment
        // Version is 4 after 4 transitions (book=0, confirm=1, checkin=2, start=3, complete=4)
        ResponseEntity<Map> updateResponse = patchAppointment(
                appointmentId, rescheduleBody("15:00"), "RECEPTIONIST", 4);

        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}

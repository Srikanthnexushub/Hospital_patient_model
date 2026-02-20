package com.ainexus.hospital.patient.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for appointment conflict detection and boundary cases.
 *
 * Verifies that:
 * - Overlapping appointments are rejected with 409
 * - Adjacent (non-overlapping) slots are accepted with 201
 * - A cancelled appointment's slot can be re-used
 */
@DisplayName("Appointment Conflict IT")
class AppointmentConflictIT extends BaseIntegrationTest {

    private static final LocalDate TOMORROW = LocalDate.now().plusDays(1);

    /** Books an appointment with an explicit duration and returns the response. */
    private ResponseEntity<Map> book(String patientId, String doctorId,
                                      LocalTime startTime, int durationMinutes) {
        HttpHeaders headers = authHeaders("RECEPTIONIST");
        Map<String, Object> body = Map.of(
                "patientId", patientId,
                "doctorId", doctorId,
                "appointmentDate", TOMORROW.toString(),
                "startTime", startTime.toString(),
                "durationMinutes", durationMinutes,
                "type", "GENERAL_CONSULTATION",
                "reason", "Test conflict check"
        );
        return restTemplate.exchange(
                baseUrl("/api/v1/appointments"),
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Map.class);
    }

    @Test
    @DisplayName("Overlapping appointment at 10:30 for 30 min (conflicts with 10:00-11:00) → 409 with 'conflicting' message")
    void overlappingAppointment_returns409WithConflictingMessage() {
        String doctorId  = seedDoctor("doctor1");
        String patientId = seedPatient("Alice", "Smith");

        // Book first appointment: 10:00 for 60 min (occupies 10:00–11:00)
        ResponseEntity<Map> first = book(patientId, doctorId, LocalTime.of(10, 0), 60);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Attempt overlapping: 10:30 for 30 min (10:30–11:00 overlaps)
        ResponseEntity<Map> conflict = book(patientId, doctorId, LocalTime.of(10, 30), 30);
        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        String message = (String) conflict.getBody().get("message");
        assertThat(message).containsIgnoringCase("conflicting");
    }

    @Test
    @DisplayName("Adjacent slot at 11:00 (immediately after 10:00-11:00 appointment) → 201 OK")
    void adjacentSlot_afterExistingAppointment_returns201() {
        String doctorId  = seedDoctor("doctor1");
        String patientId = seedPatient("Bob", "Jones");

        // Book first appointment: 10:00 for 60 min (occupies 10:00–11:00)
        ResponseEntity<Map> first = book(patientId, doctorId, LocalTime.of(10, 0), 60);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Adjacent slot: 11:00 for 30 min (starts exactly when first ends) — should succeed
        ResponseEntity<Map> adjacent = book(patientId, doctorId, LocalTime.of(11, 0), 30);
        assertThat(adjacent.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("Cancelled appointment slot can be re-booked → rebook returns 201")
    void cancelledAppointmentSlot_canBeRebooked() {
        String doctorId  = seedDoctor("doctor1");
        String patientId = seedPatient("Carol", "Brown");

        // Book appointment at 14:00 for 30 min
        ResponseEntity<Map> first = book(patientId, doctorId, LocalTime.of(14, 0), 30);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String appointmentId = (String) first.getBody().get("appointmentId");

        // Cancel the appointment (ADMIN can cancel from any status)
        Map<String, Object> cancelBody = Map.of("action", "CANCEL", "reason", "Patient request");
        ResponseEntity<Map> cancelResponse = restTemplate.exchange(
                baseUrl("/api/v1/appointments/" + appointmentId + "/status"),
                HttpMethod.PATCH,
                new HttpEntity<>(cancelBody, authHeaders("ADMIN")),
                Map.class);
        assertThat(cancelResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((String) cancelResponse.getBody().get("newStatus")).isEqualTo("CANCELLED");

        // Re-book the same slot — must succeed because the previous appointment is cancelled
        ResponseEntity<Map> rebook = book(patientId, doctorId, LocalTime.of(14, 0), 30);
        assertThat(rebook.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
}

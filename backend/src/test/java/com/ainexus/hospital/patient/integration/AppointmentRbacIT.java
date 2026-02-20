package com.ainexus.hospital.patient.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RBAC matrix integration tests for the appointment module.
 *
 * Verifies server-side role enforcement on every appointment endpoint.
 * Client-side checks are cosmetic only; this is the authoritative source of truth.
 */
@DisplayName("Appointment RBAC IT")
class AppointmentRbacIT extends BaseIntegrationTest {

    private static final LocalDate TOMORROW = LocalDate.now().plusDays(1);

    /** Builds a minimal valid book-appointment request body. */
    private Map<String, Object> bookBody(String patientId, String doctorId) {
        return Map.of(
                "patientId", patientId,
                "doctorId", doctorId,
                "appointmentDate", TOMORROW.toString(),
                "startTime", "10:00",
                "durationMinutes", 30,
                "type", "GENERAL_CONSULTATION",
                "reason", "RBAC test"
        );
    }

    // ── POST /api/v1/appointments ─────────────────────────────────────────

    @Test
    @DisplayName("POST /appointments as NURSE → 403 (NURSE cannot book appointments)")
    void bookAppointment_asNurse_returns403() {
        String doctorId  = seedDoctor("doctor1");
        String patientId = seedPatient("Test", "Nurse");

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl("/api/v1/appointments"),
                HttpMethod.POST,
                new HttpEntity<>(bookBody(patientId, doctorId), authHeaders("NURSE")),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("POST /appointments as DOCTOR → 403 (DOCTOR cannot book appointments)")
    void bookAppointment_asDoctor_returns403() {
        String doctorId  = seedDoctor("doctor1");
        String patientId = seedPatient("Test", "Doctor");

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl("/api/v1/appointments"),
                HttpMethod.POST,
                new HttpEntity<>(bookBody(patientId, doctorId), authHeaders("DOCTOR")),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── PATCH /api/v1/appointments/{id}/status (CONFIRM) ─────────────────

    @Test
    @DisplayName("CONFIRM as NURSE → 403 (NURSE cannot confirm appointments)")
    void confirmAppointment_asNurse_returns403() {
        String doctorId      = seedDoctor("doctor1");
        String patientId     = seedPatient("Confirm", "Nurse");
        String appointmentId = bookAppointment(patientId, doctorId, TOMORROW, LocalTime.of(11, 0));

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl("/api/v1/appointments/" + appointmentId + "/status"),
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("action", "CONFIRM"), authHeaders("NURSE")),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── PATCH /api/v1/appointments/{id}/status (CHECK_IN) ────────────────

    @Test
    @DisplayName("CHECK_IN as NURSE → 200 (NURSE can check in patients)")
    void checkInAppointment_asNurse_returns200() {
        String doctorId      = seedDoctor("doctor1");
        String patientId     = seedPatient("CheckIn", "Nurse");
        String appointmentId = bookAppointment(patientId, doctorId, TOMORROW, LocalTime.of(12, 0));

        // Must reach CONFIRMED before CHECK_IN
        restTemplate.exchange(
                baseUrl("/api/v1/appointments/" + appointmentId + "/status"),
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("action", "CONFIRM"), authHeaders("RECEPTIONIST")),
                Map.class);

        // Now NURSE checks in
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl("/api/v1/appointments/" + appointmentId + "/status"),
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("action", "CHECK_IN"), authHeaders("NURSE")),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((String) response.getBody().get("newStatus")).isEqualTo("CHECKED_IN");
    }

    // ── POST /api/v1/appointments/{id}/notes ─────────────────────────────

    @Test
    @DisplayName("POST /appointments/{id}/notes as RECEPTIONIST → 403 (only DOCTOR/ADMIN can add notes)")
    void addNotes_asReceptionist_returns403() {
        String doctorId      = seedDoctor("doctor1");
        String patientId     = seedPatient("Notes", "Receptionist");
        String appointmentId = bookAppointment(patientId, doctorId, TOMORROW, LocalTime.of(13, 0));

        Map<String, Object> notesBody = Map.of(
                "subjective", "Patient reports headache",
                "objective", "BP 120/80",
                "assessment", "Tension headache",
                "plan", "Ibuprofen 400mg"
        );

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl("/api/v1/appointments/" + appointmentId + "/notes"),
                HttpMethod.POST,
                new HttpEntity<>(notesBody, authHeaders("RECEPTIONIST")),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── PATCH /api/v1/appointments/{id} (update appointment) ─────────────

    @Test
    @DisplayName("PATCH /appointments/{id} (update) as NURSE → 403 (NURSE cannot update appointments)")
    void updateAppointment_asNurse_returns403() {
        String doctorId      = seedDoctor("doctor1");
        String patientId     = seedPatient("Update", "Nurse");
        String appointmentId = bookAppointment(patientId, doctorId, TOMORROW, LocalTime.of(14, 0));

        Map<String, Object> updateBody = Map.of(
                "startTime", "15:00",
                "durationMinutes", 30
        );

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl("/api/v1/appointments/" + appointmentId),
                HttpMethod.PATCH,
                new HttpEntity<>(updateBody, authHeaders("NURSE")),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}

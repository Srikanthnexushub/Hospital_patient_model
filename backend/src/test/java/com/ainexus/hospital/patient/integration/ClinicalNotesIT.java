package com.ainexus.hospital.patient.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the clinical notes endpoint.
 *
 * Verifies:
 * - DOCTOR who owns the appointment can POST and GET notes (with privateNotes)
 * - RECEPTIONIST can GET notes but privateNotes is null (redacted)
 * - A different DOCTOR (different userId) cannot POST notes → 403
 * - ADMIN can POST notes regardless of appointment ownership
 */
@DisplayName("Clinical Notes IT")
class ClinicalNotesIT extends BaseIntegrationTest {

    private static final LocalDate TOMORROW = LocalDate.now().plusDays(1);

    /** A valid SOAP notes request body. */
    private Map<String, Object> notesBody() {
        return Map.of(
                "subjective", "Patient reports persistent headache for 3 days",
                "objective", "BP 130/85, HR 78, Temp 37.1C",
                "assessment", "Tension-type headache",
                "plan", "Ibuprofen 400mg TID for 5 days; review in 1 week",
                "privateNotes", "Suspect stress-related; discuss at next visit"
        );
    }

    /** Drives appointment to IN_PROGRESS (book → confirm → check_in → start). */
    private String driveToInProgress(String patientId, String doctorId, LocalTime startTime) {
        String appointmentId = bookAppointment(patientId, doctorId, TOMORROW, startTime);

        HttpHeaders receptionistHeaders = authHeaders("RECEPTIONIST");
        HttpHeaders doctorHeaders       = authHeaders("DOCTOR");

        restTemplate.exchange(
                baseUrl("/api/v1/appointments/" + appointmentId + "/status"),
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("action", "CONFIRM"), receptionistHeaders),
                Map.class);

        restTemplate.exchange(
                baseUrl("/api/v1/appointments/" + appointmentId + "/status"),
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("action", "CHECK_IN"), receptionistHeaders),
                Map.class);

        restTemplate.exchange(
                baseUrl("/api/v1/appointments/" + appointmentId + "/status"),
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("action", "START"), doctorHeaders),
                Map.class);

        return appointmentId;
    }

    @Test
    @DisplayName("DOCTOR (appointment owner) can POST notes → 200")
    void postNotes_asOwningDoctor_returns200() {
        String doctorId      = seedDoctor("doctor1");
        String patientId     = seedPatient("Notes", "Doctor");
        String appointmentId = driveToInProgress(patientId, doctorId, LocalTime.of(9, 0));

        HttpHeaders doctorHeaders = authHeaders("DOCTOR");
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl("/api/v1/appointments/" + appointmentId + "/notes"),
                HttpMethod.POST,
                new HttpEntity<>(notesBody(), doctorHeaders),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("DOCTOR can GET notes with privateNotes present")
    void getNotes_asOwningDoctor_includesPrivateNotes() {
        String doctorId      = seedDoctor("doctor1");
        String patientId     = seedPatient("Notes", "GetDoctor");
        String appointmentId = driveToInProgress(patientId, doctorId, LocalTime.of(10, 0));

        HttpHeaders doctorHeaders = authHeaders("DOCTOR");

        // POST notes first
        restTemplate.exchange(
                baseUrl("/api/v1/appointments/" + appointmentId + "/notes"),
                HttpMethod.POST,
                new HttpEntity<>(notesBody(), doctorHeaders),
                Map.class);

        // GET notes as the owning DOCTOR
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl("/api/v1/appointments/" + appointmentId + "/notes"),
                HttpMethod.GET,
                new HttpEntity<>(doctorHeaders),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("privateNotes")).isNotNull();
    }

    @Test
    @DisplayName("RECEPTIONIST can GET notes but privateNotes is null (redacted)")
    void getNotes_asReceptionist_privateNotesRedacted() {
        String doctorId      = seedDoctor("doctor1");
        String patientId     = seedPatient("Notes", "Redact");
        String appointmentId = driveToInProgress(patientId, doctorId, LocalTime.of(11, 0));

        HttpHeaders doctorHeaders = authHeaders("DOCTOR");

        // POST notes as DOCTOR
        restTemplate.exchange(
                baseUrl("/api/v1/appointments/" + appointmentId + "/notes"),
                HttpMethod.POST,
                new HttpEntity<>(notesBody(), doctorHeaders),
                Map.class);

        // GET notes as RECEPTIONIST
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl("/api/v1/appointments/" + appointmentId + "/notes"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders("RECEPTIONIST")),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("privateNotes")).isNull();
    }

    @Test
    @DisplayName("Different DOCTOR (different userId) cannot POST notes → 403")
    void postNotes_asDifferentDoctor_returns403() {
        String doctorId      = seedDoctor("doctor1");
        String patientId     = seedPatient("Notes", "OtherDoctor");
        String appointmentId = driveToInProgress(patientId, doctorId, LocalTime.of(13, 0));

        // Seed a second, different doctor
        seedDoctorWithId("U2025002", "doctor2");

        // Attempt to POST notes as the second doctor (different userId)
        HttpHeaders otherDoctorHeaders = authHeadersForUser("DOCTOR", "U2025002", "doctor2");
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl("/api/v1/appointments/" + appointmentId + "/notes"),
                HttpMethod.POST,
                new HttpEntity<>(notesBody(), otherDoctorHeaders),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("ADMIN can POST notes on any appointment → 200")
    void postNotes_asAdmin_returns200() {
        String doctorId      = seedDoctor("doctor1");
        String patientId     = seedPatient("Notes", "Admin");
        String appointmentId = driveToInProgress(patientId, doctorId, LocalTime.of(14, 0));

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl("/api/v1/appointments/" + appointmentId + "/notes"),
                HttpMethod.POST,
                new HttpEntity<>(notesBody(), authHeaders("ADMIN")),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}

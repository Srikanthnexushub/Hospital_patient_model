package com.ainexus.hospital.patient.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for patient appointment history endpoint.
 *
 * Verifies:
 * - ADMIN sees all appointments for a patient
 * - DOCTOR sees only their own appointments for that patient
 * - RECEPTIONIST sees all appointments for a patient
 * - Request for non-existent patient returns 404
 */
@DisplayName("Patient Appointment History IT")
class PatientAppointmentHistoryIT extends BaseIntegrationTest {

    private static final String PATIENT_ID = "P2025001";
    private static final LocalDate BASE_DATE = LocalDate.now().plusDays(1);

    @BeforeEach
    void seedSpecificPatient() {
        // Insert patient with a known, stable ID so assertions reference it reliably
        jdbcTemplate.update("""
                INSERT INTO patients
                  (patient_id, first_name, last_name, date_of_birth, gender, phone,
                   blood_group, status, version, created_at, updated_at, created_by, updated_by)
                VALUES (?, 'History', 'Patient', '1990-06-15', 'FEMALE', '9000000001',
                        'A_POS', 'ACTIVE', 0, NOW(), NOW(), 'test', 'test')
                ON CONFLICT (patient_id) DO NOTHING
                """, PATIENT_ID);
    }

    /**
     * Books an appointment with the primary doctor and returns its ID.
     * Appointments are scheduled on consecutive days to avoid conflicts.
     */
    private String bookForPrimaryDoctor(String doctorId, int dayOffset) {
        return bookAppointment(PATIENT_ID, doctorId, BASE_DATE.plusDays(dayOffset), LocalTime.of(9, 0));
    }

    @Test
    @DisplayName("ADMIN sees all 3 appointments for the patient (totalElements=3)")
    void getHistory_asAdmin_returnsAllAppointments() {
        String doctorId  = seedDoctor("doctor1");
        String doctor2Id = seedDoctorWithId("U2025002", "doctor2");

        // Seed 3 appointments: 2 with doctor1, 1 with doctor2
        bookForPrimaryDoctor(doctorId, 0);
        bookForPrimaryDoctor(doctorId, 1);
        bookAppointment(PATIENT_ID, doctor2Id, BASE_DATE.plusDays(2), LocalTime.of(10, 0));

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + PATIENT_ID + "/appointments"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders("ADMIN")),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Object totalElements = response.getBody().get("totalElements");
        assertThat(((Number) totalElements).intValue()).isEqualTo(3);
    }

    @Test
    @DisplayName("DOCTOR sees only their own 2 appointments (not doctor2's appointment)")
    void getHistory_asDoctor_returnsOnlyOwnAppointments() {
        String doctorId  = seedDoctor("doctor1");
        String doctor2Id = seedDoctorWithId("U2025002", "doctor2");

        // 2 appointments with doctor1, 1 with doctor2
        bookForPrimaryDoctor(doctorId, 0);
        bookForPrimaryDoctor(doctorId, 1);
        bookAppointment(PATIENT_ID, doctor2Id, BASE_DATE.plusDays(2), LocalTime.of(10, 0));

        // DOCTOR JWT for doctor1 (userId = "U2025001" — matches seedDoctor)
        HttpHeaders doctorHeaders = authHeaders("DOCTOR");
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + PATIENT_ID + "/appointments"),
                HttpMethod.GET,
                new HttpEntity<>(doctorHeaders),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Object totalElements = response.getBody().get("totalElements");
        // Doctor sees only their own 2 appointments
        assertThat(((Number) totalElements).intValue()).isEqualTo(2);
    }

    @Test
    @DisplayName("RECEPTIONIST sees all 3 appointments for the patient (totalElements=3)")
    void getHistory_asReceptionist_returnsAllAppointments() {
        String doctorId  = seedDoctor("doctor1");
        String doctor2Id = seedDoctorWithId("U2025002", "doctor2");

        bookForPrimaryDoctor(doctorId, 0);
        bookForPrimaryDoctor(doctorId, 1);
        bookAppointment(PATIENT_ID, doctor2Id, BASE_DATE.plusDays(2), LocalTime.of(10, 0));

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl("/api/v1/patients/" + PATIENT_ID + "/appointments"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders("RECEPTIONIST")),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Object totalElements = response.getBody().get("totalElements");
        assertThat(((Number) totalElements).intValue()).isEqualTo(3);
    }

    @Test
    @DisplayName("GET history for non-existent patient → 404")
    void getHistory_nonExistentPatient_returns404() {
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl("/api/v1/patients/P9999999/appointments"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders("ADMIN")),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}

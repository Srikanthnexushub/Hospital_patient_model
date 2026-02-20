package com.ainexus.hospital.patient.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for appointment cancellation and NO_SHOW flows.
 *
 * Verifies:
 * - RECEPTIONIST can mark a CONFIRMED appointment as NO_SHOW
 * - NO_SHOW appointment is retained (soft state, not deleted)
 * - A NO_SHOW slot can be re-booked immediately
 * - CANCEL without a reason body field → 400
 */
@DisplayName("Appointment Cancellation IT")
class AppointmentCancellationIT extends BaseIntegrationTest {

    private static final LocalDate TOMORROW = LocalDate.now().plusDays(1);

    /** Confirms a SCHEDULED appointment and returns the response. */
    private ResponseEntity<Map> confirm(String appointmentId) {
        return restTemplate.exchange(
                baseUrl("/api/v1/appointments/" + appointmentId + "/status"),
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("action", "CONFIRM"), authHeaders("RECEPTIONIST")),
                Map.class);
    }

    @Test
    @DisplayName("RECEPTIONIST marks CONFIRMED appointment as NO_SHOW → 200 newStatus=NO_SHOW")
    void noShow_asReceptionist_returns200WithNoShowStatus() {
        String doctorId      = seedDoctor("doctor1");
        String patientId     = seedPatient("NoShow", "Test");
        String appointmentId = bookAppointment(patientId, doctorId, TOMORROW, LocalTime.of(9, 0));

        // Confirm first
        assertThat(confirm(appointmentId).getStatusCode()).isEqualTo(HttpStatus.OK);

        // Mark as NO_SHOW
        ResponseEntity<Map> noShowResponse = restTemplate.exchange(
                baseUrl("/api/v1/appointments/" + appointmentId + "/status"),
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("action", "NO_SHOW"), authHeaders("RECEPTIONIST")),
                Map.class);

        assertThat(noShowResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((String) noShowResponse.getBody().get("newStatus")).isEqualTo("NO_SHOW");
    }

    @Test
    @DisplayName("NO_SHOW appointment is retained and readable with status=NO_SHOW")
    void noShow_appointmentRetained_getReturnsNoShowStatus() {
        String doctorId      = seedDoctor("doctor1");
        String patientId     = seedPatient("NoShow", "Retain");
        String appointmentId = bookAppointment(patientId, doctorId, TOMORROW, LocalTime.of(10, 0));

        confirm(appointmentId);

        restTemplate.exchange(
                baseUrl("/api/v1/appointments/" + appointmentId + "/status"),
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("action", "NO_SHOW"), authHeaders("RECEPTIONIST")),
                Map.class);

        // GET the appointment — must still exist with status NO_SHOW (not deleted)
        ResponseEntity<Map> getResponse = restTemplate.exchange(
                baseUrl("/api/v1/appointments/" + appointmentId),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders("RECEPTIONIST")),
                Map.class);

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((String) getResponse.getBody().get("status")).isEqualTo("NO_SHOW");
    }

    @Test
    @DisplayName("Slot freed by NO_SHOW can be immediately re-booked → 201")
    void noShowSlot_canBeRebooked_returns201() {
        String doctorId      = seedDoctor("doctor1");
        String patientId     = seedPatient("NoShow", "Rebook");
        String appointmentId = bookAppointment(patientId, doctorId, TOMORROW, LocalTime.of(11, 0));

        confirm(appointmentId);

        // Mark as NO_SHOW
        restTemplate.exchange(
                baseUrl("/api/v1/appointments/" + appointmentId + "/status"),
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("action", "NO_SHOW"), authHeaders("RECEPTIONIST")),
                Map.class);

        // Re-book the same slot — must succeed because NO_SHOW does not hold the slot
        HttpHeaders receptionistHeaders = authHeaders("RECEPTIONIST");
        Map<String, Object> rebookBody = Map.of(
                "patientId", patientId,
                "doctorId", doctorId,
                "appointmentDate", TOMORROW.toString(),
                "startTime", "11:00",
                "durationMinutes", 30,
                "type", "GENERAL_CONSULTATION",
                "reason", "Rebook after no-show"
        );
        ResponseEntity<Map> rebookResponse = restTemplate.exchange(
                baseUrl("/api/v1/appointments"),
                HttpMethod.POST,
                new HttpEntity<>(rebookBody, receptionistHeaders),
                Map.class);

        assertThat(rebookResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("CANCEL without a reason field → 400 Bad Request")
    void cancelWithoutReason_returns400() {
        String doctorId      = seedDoctor("doctor1");
        String patientId     = seedPatient("Cancel", "NoReason");
        String appointmentId = bookAppointment(patientId, doctorId, TOMORROW, LocalTime.of(13, 0));

        confirm(appointmentId);

        // Attempt CANCEL with no reason provided
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl("/api/v1/appointments/" + appointmentId + "/status"),
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("action", "CANCEL"), authHeaders("RECEPTIONIST")),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("ADMIN can CANCEL from any status using the escape hatch")
    void adminCancelEscapeHatch_fromScheduled_returns200() {
        String doctorId      = seedDoctor("doctor1");
        String patientId     = seedPatient("Cancel", "Admin");
        String appointmentId = bookAppointment(patientId, doctorId, TOMORROW, LocalTime.of(14, 0));

        // Cancel directly from SCHEDULED (ADMIN escape hatch) with reason
        Map<String, Object> cancelBody = Map.of("action", "CANCEL", "reason", "Admin override");
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl("/api/v1/appointments/" + appointmentId + "/status"),
                HttpMethod.PATCH,
                new HttpEntity<>(cancelBody, authHeaders("ADMIN")),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((String) response.getBody().get("newStatus")).isEqualTo("CANCELLED");
    }
}

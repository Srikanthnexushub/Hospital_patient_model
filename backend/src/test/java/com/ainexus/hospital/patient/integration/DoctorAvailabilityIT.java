package com.ainexus.hospital.patient.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the doctor availability endpoint.
 *
 * Verifies that:
 * - Booked 60-min slot blocks the two 30-min sub-slots (09:00 and 09:30)
 * - After cancellation, all slots for the day become available again
 */
@DisplayName("Doctor Availability IT")
class DoctorAvailabilityIT extends BaseIntegrationTest {

    private static final LocalDate TOMORROW = LocalDate.now().plusDays(1);

    @Test
    @DisplayName("CONFIRMED 60-min appointment blocks 09:00 and 09:30 slots; after CANCEL all slots available")
    void bookedSlot_markedUnavailable_thenAvailableAfterCancellation() {
        String doctorId  = seedDoctor("doctor1");
        String patientId = seedPatient("Avail", "Test");

        // Book 60-min appointment at 09:00 (occupies 09:00–10:00, blocks two 30-min sub-slots)
        String appointmentId = bookAppointment(patientId, doctorId, TOMORROW, LocalTime.of(9, 0), 60);

        // Confirm the appointment (SCHEDULED → CONFIRMED)
        Map<String, Object> confirmBody = Map.of("action", "CONFIRM");
        ResponseEntity<Map> confirmResponse = restTemplate.exchange(
                baseUrl("/api/v1/appointments/" + appointmentId + "/status"),
                HttpMethod.PATCH,
                new HttpEntity<>(confirmBody, authHeaders("RECEPTIONIST")),
                Map.class);
        assertThat(confirmResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // GET availability for the doctor on TOMORROW
        ResponseEntity<Map> availResponse = restTemplate.exchange(
                baseUrl("/api/v1/doctors/" + doctorId + "/availability?date=" + TOMORROW),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders("RECEPTIONIST")),
                Map.class);

        assertThat(availResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> slots = (List<Map<String, Object>>) availResponse.getBody().get("slots");
        assertThat(slots).isNotNull().isNotEmpty();

        // Slots 09:00 and 09:30 must be unavailable
        Map<String, Object> slot0900 = slots.stream()
                .filter(s -> "09:00".equals(s.get("startTime")) || "09:00:00".equals(s.get("startTime")))
                .findFirst()
                .orElse(null);
        Map<String, Object> slot0930 = slots.stream()
                .filter(s -> "09:30".equals(s.get("startTime")) || "09:30:00".equals(s.get("startTime")))
                .findFirst()
                .orElse(null);

        assertThat(slot0900).isNotNull();
        assertThat(slot0930).isNotNull();
        assertThat((Boolean) slot0900.get("available")).isFalse();
        assertThat((Boolean) slot0930.get("available")).isFalse();

        // Total number of available=true slots before cancellation
        long availableBeforeCancel = slots.stream()
                .filter(s -> Boolean.TRUE.equals(s.get("available")))
                .count();

        // Cancel the appointment (ADMIN escape hatch — cancels from any status with a reason)
        Map<String, Object> cancelBody = Map.of("action", "CANCEL", "reason", "Availability test");
        ResponseEntity<Map> cancelResponse = restTemplate.exchange(
                baseUrl("/api/v1/appointments/" + appointmentId + "/status"),
                HttpMethod.PATCH,
                new HttpEntity<>(cancelBody, authHeaders("ADMIN")),
                Map.class);
        assertThat(cancelResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((String) cancelResponse.getBody().get("newStatus")).isEqualTo("CANCELLED");

        // GET availability again — all 20 slots should now be available
        ResponseEntity<Map> availAfterCancel = restTemplate.exchange(
                baseUrl("/api/v1/doctors/" + doctorId + "/availability?date=" + TOMORROW),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders("RECEPTIONIST")),
                Map.class);

        assertThat(availAfterCancel.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> slotsAfter = (List<Map<String, Object>>) availAfterCancel.getBody().get("slots");
        assertThat(slotsAfter).isNotNull();

        long availableAfterCancel = slotsAfter.stream()
                .filter(s -> Boolean.TRUE.equals(s.get("available")))
                .count();

        // All slots must now be available (2 more than before, corresponding to the freed 60-min block)
        assertThat(availableAfterCancel).isGreaterThan(availableBeforeCancel);

        // Specifically confirm the 09:00 and 09:30 slots are now available
        Map<String, Object> slot0900After = slotsAfter.stream()
                .filter(s -> "09:00".equals(s.get("startTime")) || "09:00:00".equals(s.get("startTime")))
                .findFirst()
                .orElse(null);
        Map<String, Object> slot0930After = slotsAfter.stream()
                .filter(s -> "09:30".equals(s.get("startTime")) || "09:30:00".equals(s.get("startTime")))
                .findFirst()
                .orElse(null);

        assertThat(slot0900After).isNotNull();
        assertThat(slot0930After).isNotNull();
        assertThat((Boolean) slot0900After.get("available")).isTrue();
        assertThat((Boolean) slot0930After.get("available")).isTrue();
    }

    @Test
    @DisplayName("GET availability for non-existent doctor → 404")
    void getAvailability_nonExistentDoctor_returns404() {
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl("/api/v1/doctors/non-existent-doctor-id/availability?date=" + TOMORROW),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders("RECEPTIONIST")),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}

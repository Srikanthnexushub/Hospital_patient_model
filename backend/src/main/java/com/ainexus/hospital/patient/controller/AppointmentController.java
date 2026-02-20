package com.ainexus.hospital.patient.controller;

import com.ainexus.hospital.patient.dto.request.AppointmentStatusChangeRequest;
import com.ainexus.hospital.patient.dto.request.BookAppointmentRequest;
import com.ainexus.hospital.patient.dto.request.UpdateAppointmentRequest;
import com.ainexus.hospital.patient.dto.response.*;
import com.ainexus.hospital.patient.entity.AppointmentStatus;
import com.ainexus.hospital.patient.entity.AppointmentType;
import com.ainexus.hospital.patient.service.AppointmentService;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * HTTP layer — no business logic. Delegates entirely to AppointmentService.
 *
 * NOTE: /appointments/today must be declared BEFORE /appointments/{appointmentId}
 * to prevent Spring MVC from matching "today" as an ID.
 */
@RestController
@RequestMapping("/api/v1")
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    // ── US1: Book Appointment ─────────────────────────────────────────────────

    @PostMapping("/appointments")
    public ResponseEntity<AppointmentResponse> bookAppointment(
            @Valid @RequestBody BookAppointmentRequest request) {
        setTrace("BOOK_APPOINTMENT");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(appointmentService.bookAppointment(request));
    }

    // ── US2: List/Search — /today BEFORE /{id} ───────────────────────────────

    @GetMapping("/appointments/today")
    public ResponseEntity<PagedResponse<AppointmentSummaryResponse>> getTodayAppointments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        setTrace("GET_TODAY_APPOINTMENTS");
        return ResponseEntity.ok(appointmentService.getTodayAppointments(page, size));
    }

    @GetMapping("/appointments")
    public ResponseEntity<PagedResponse<AppointmentSummaryResponse>> listAppointments(
            @RequestParam(required = false) String doctorId,
            @RequestParam(required = false) String patientId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(required = false) AppointmentType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        setTrace("LIST_APPOINTMENTS");
        return ResponseEntity.ok(appointmentService.listAppointments(
                doctorId, patientId, date, dateFrom, dateTo, status, type, page, size));
    }

    @GetMapping("/appointments/{appointmentId}")
    public ResponseEntity<AppointmentResponse> getAppointment(
            @PathVariable String appointmentId) {
        setTrace("GET_APPOINTMENT");
        return ResponseEntity.ok(appointmentService.getAppointment(appointmentId));
    }

    // ── US4: Update Appointment ───────────────────────────────────────────────

    @PatchMapping("/appointments/{appointmentId}")
    public ResponseEntity<AppointmentResponse> updateAppointment(
            @PathVariable String appointmentId,
            @RequestHeader(value = "If-Match", required = false) Integer version,
            @Valid @RequestBody UpdateAppointmentRequest request) {
        setTrace("UPDATE_APPOINTMENT");
        return ResponseEntity.ok(appointmentService.updateAppointment(appointmentId, version, request));
    }

    // ── US3: Status Lifecycle ─────────────────────────────────────────────────

    @PatchMapping("/appointments/{appointmentId}/status")
    public ResponseEntity<AppointmentStatusChangeResponse> changeStatus(
            @PathVariable String appointmentId,
            @Valid @RequestBody AppointmentStatusChangeRequest request) {
        setTrace("CHANGE_APPOINTMENT_STATUS");
        return ResponseEntity.ok(appointmentService.changeStatus(appointmentId, request));
    }

    // ── US7: Patient Appointment History ─────────────────────────────────────

    @GetMapping("/patients/{patientId}/appointments")
    public ResponseEntity<PagedResponse<AppointmentSummaryResponse>> getPatientAppointmentHistory(
            @PathVariable String patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        setTrace("GET_PATIENT_APPOINTMENTS");
        return ResponseEntity.ok(appointmentService.getPatientAppointmentHistory(patientId, page, size));
    }

    private void setTrace(String operation) {
        MDC.put("traceId", UUID.randomUUID().toString().substring(0, 8));
        MDC.put("operation", operation);
    }
}

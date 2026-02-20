package com.ainexus.hospital.patient.controller;

import com.ainexus.hospital.patient.dto.request.RecordVitalsRequest;
import com.ainexus.hospital.patient.dto.response.VitalsResponse;
import com.ainexus.hospital.patient.service.VitalsService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class VitalsController {

    private final VitalsService vitalsService;

    public VitalsController(VitalsService vitalsService) {
        this.vitalsService = vitalsService;
    }

    /** US1 — Record or replace vitals for an appointment (upsert). */
    @PostMapping("/api/v1/appointments/{appointmentId}/vitals")
    public ResponseEntity<VitalsResponse> recordVitals(
            @PathVariable String appointmentId,
            @Valid @RequestBody RecordVitalsRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(vitalsService.recordVitals(appointmentId, request));
    }

    /** US1 — Get vitals for a specific appointment. */
    @GetMapping("/api/v1/appointments/{appointmentId}/vitals")
    public ResponseEntity<VitalsResponse> getVitalsByAppointment(
            @PathVariable String appointmentId) {
        return ResponseEntity.ok(vitalsService.getVitalsByAppointment(appointmentId));
    }

    /** US1 — Paginated vitals history for a patient, sorted most-recent first. */
    @GetMapping("/api/v1/patients/{patientId}/vitals")
    public ResponseEntity<Page<VitalsResponse>> getVitalsByPatient(
            @PathVariable String patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(vitalsService.getVitalsByPatient(patientId, page, size));
    }
}

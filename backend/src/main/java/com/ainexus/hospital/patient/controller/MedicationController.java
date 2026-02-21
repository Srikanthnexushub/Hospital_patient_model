package com.ainexus.hospital.patient.controller;

import com.ainexus.hospital.patient.dto.request.PrescribeMedicationRequest;
import com.ainexus.hospital.patient.dto.request.UpdateMedicationRequest;
import com.ainexus.hospital.patient.dto.response.MedicationResponse;
import com.ainexus.hospital.patient.service.MedicationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/patients/{patientId}/medications")
public class MedicationController {

    private final MedicationService medicationService;

    public MedicationController(MedicationService medicationService) {
        this.medicationService = medicationService;
    }

    /** US3 — Prescribe a medication for the patient. */
    @PostMapping
    public ResponseEntity<MedicationResponse> prescribeMedication(
            @PathVariable String patientId,
            @Valid @RequestBody PrescribeMedicationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(medicationService.prescribeMedication(patientId, request));
    }

    /** US3 — List medications; active by default. Use ?status=ALL for full history. */
    @GetMapping
    public ResponseEntity<List<MedicationResponse>> listMedications(
            @PathVariable String patientId,
            @RequestParam(defaultValue = "ACTIVE") String status) {
        return ResponseEntity.ok(medicationService.listMedications(patientId, status));
    }

    /** US3 — Partial update of a medication (including discontinue). */
    @PatchMapping("/{medicationId}")
    public ResponseEntity<MedicationResponse> updateMedication(
            @PathVariable String patientId,
            @PathVariable UUID medicationId,
            @RequestBody UpdateMedicationRequest request) {
        return ResponseEntity.ok(medicationService.updateMedication(patientId, medicationId, request));
    }
}

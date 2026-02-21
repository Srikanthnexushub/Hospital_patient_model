package com.ainexus.hospital.patient.controller;

import com.ainexus.hospital.patient.dto.request.RecordAllergyRequest;
import com.ainexus.hospital.patient.dto.response.AllergyResponse;
import com.ainexus.hospital.patient.service.AllergyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/patients/{patientId}/allergies")
public class AllergyController {

    private final AllergyService allergyService;

    public AllergyController(AllergyService allergyService) {
        this.allergyService = allergyService;
    }

    /** US4 — Record a new allergy for the patient. */
    @PostMapping
    public ResponseEntity<AllergyResponse> recordAllergy(
            @PathVariable String patientId,
            @Valid @RequestBody RecordAllergyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(allergyService.recordAllergy(patientId, request));
    }

    /** US4 — List active allergies for the patient. */
    @GetMapping
    public ResponseEntity<List<AllergyResponse>> listAllergies(
            @PathVariable String patientId) {
        return ResponseEntity.ok(allergyService.listAllergies(patientId));
    }

    /** US4 — Soft-delete an allergy (sets active=false). */
    @DeleteMapping("/{allergyId}")
    public ResponseEntity<Void> deleteAllergy(
            @PathVariable String patientId,
            @PathVariable UUID allergyId) {
        allergyService.deleteAllergy(patientId, allergyId);
        return ResponseEntity.noContent().build();
    }
}

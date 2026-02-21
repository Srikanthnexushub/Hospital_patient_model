package com.ainexus.hospital.patient.controller;

import com.ainexus.hospital.patient.dto.InteractionCheckRequest;
import com.ainexus.hospital.patient.dto.InteractionCheckResponse;
import com.ainexus.hospital.patient.dto.InteractionSummaryResponse;
import com.ainexus.hospital.patient.service.DrugInteractionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class DrugInteractionController {

    private final DrugInteractionService drugInteractionService;

    public DrugInteractionController(DrugInteractionService drugInteractionService) {
        this.drugInteractionService = drugInteractionService;
    }

    /** US3 — Check a drug against patient's active meds and allergies. Roles: DOCTOR, ADMIN. */
    @PostMapping("/patients/{patientId}/interaction-check")
    public ResponseEntity<InteractionCheckResponse> checkInteraction(
            @PathVariable String patientId,
            @Valid @RequestBody InteractionCheckRequest request) {
        return ResponseEntity.ok(drugInteractionService.checkInteraction(patientId, request));
    }

    /** US3 — Summary of all known interactions across patient's active medications. Roles: DOCTOR, NURSE, ADMIN. */
    @GetMapping("/patients/{patientId}/interaction-summary")
    public ResponseEntity<InteractionSummaryResponse> getInteractionSummary(
            @PathVariable String patientId) {
        return ResponseEntity.ok(drugInteractionService.getInteractionSummary(patientId));
    }
}

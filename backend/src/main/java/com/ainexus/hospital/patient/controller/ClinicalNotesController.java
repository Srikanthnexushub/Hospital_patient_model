package com.ainexus.hospital.patient.controller;

import com.ainexus.hospital.patient.dto.request.ClinicalNotesRequest;
import com.ainexus.hospital.patient.dto.response.ClinicalNotesResponse;
import com.ainexus.hospital.patient.service.ClinicalNotesService;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/appointments")
public class ClinicalNotesController {

    private final ClinicalNotesService clinicalNotesService;

    public ClinicalNotesController(ClinicalNotesService clinicalNotesService) {
        this.clinicalNotesService = clinicalNotesService;
    }

    // ── US6: Clinical Notes ───────────────────────────────────────────────────

    @PostMapping("/{appointmentId}/notes")
    public ResponseEntity<ClinicalNotesResponse> upsertNotes(
            @PathVariable String appointmentId,
            @Valid @RequestBody ClinicalNotesRequest request) {
        MDC.put("traceId", UUID.randomUUID().toString().substring(0, 8));
        return ResponseEntity.ok(clinicalNotesService.upsertNotes(appointmentId, request));
    }

    @GetMapping("/{appointmentId}/notes")
    public ResponseEntity<ClinicalNotesResponse> getNotes(
            @PathVariable String appointmentId) {
        MDC.put("traceId", UUID.randomUUID().toString().substring(0, 8));
        return ResponseEntity.ok(clinicalNotesService.getNotes(appointmentId));
    }
}

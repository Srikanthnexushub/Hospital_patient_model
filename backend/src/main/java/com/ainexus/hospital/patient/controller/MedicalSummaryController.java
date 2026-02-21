package com.ainexus.hospital.patient.controller;

import com.ainexus.hospital.patient.dto.response.MedicalSummaryResponse;
import com.ainexus.hospital.patient.service.MedicalSummaryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/patients/{patientId}/medical-summary")
public class MedicalSummaryController {

    private final MedicalSummaryService medicalSummaryService;

    public MedicalSummaryController(MedicalSummaryService medicalSummaryService) {
        this.medicalSummaryService = medicalSummaryService;
    }

    /** US5 — Return the patient's complete clinical snapshot. */
    @GetMapping
    public ResponseEntity<MedicalSummaryResponse> getMedicalSummary(
            @PathVariable String patientId) {
        return ResponseEntity.ok(medicalSummaryService.getMedicalSummary(patientId));
    }
}

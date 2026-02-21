package com.ainexus.hospital.patient.controller;

import com.ainexus.hospital.patient.dto.ClinicalAlertResponse;
import com.ainexus.hospital.patient.dto.DismissAlertRequest;
import com.ainexus.hospital.patient.entity.AlertSeverity;
import com.ainexus.hospital.patient.entity.AlertStatus;
import com.ainexus.hospital.patient.service.ClinicalAlertService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ClinicalAlertController {

    private final ClinicalAlertService clinicalAlertService;

    public ClinicalAlertController(ClinicalAlertService clinicalAlertService) {
        this.clinicalAlertService = clinicalAlertService;
    }

    /** US4 — All alerts for a specific patient. Roles: DOCTOR, NURSE, ADMIN. */
    @GetMapping("/patients/{patientId}/alerts")
    public ResponseEntity<Page<ClinicalAlertResponse>> getPatientAlerts(
            @PathVariable String patientId,
            @RequestParam(required = false) AlertStatus status,
            @RequestParam(required = false) AlertSeverity severity,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(clinicalAlertService.getPatientAlerts(
                patientId, status, severity,
                PageRequest.of(page, size, Sort.by("createdAt").descending())));
    }

    /** US4 — Global alert feed (DOCTOR scoped to own patients; ADMIN/NURSE see all). */
    @GetMapping("/alerts")
    public ResponseEntity<Page<ClinicalAlertResponse>> getGlobalAlerts(
            @RequestParam(required = false) AlertStatus status,
            @RequestParam(required = false) AlertSeverity severity,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(clinicalAlertService.getGlobalAlerts(
                status, severity,
                PageRequest.of(page, size, Sort.by("createdAt").descending())));
    }

    /** US4 — Acknowledge an alert. Roles: DOCTOR, NURSE, ADMIN. */
    @PatchMapping("/alerts/{alertId}/acknowledge")
    public ResponseEntity<ClinicalAlertResponse> acknowledgeAlert(@PathVariable UUID alertId) {
        return ResponseEntity.ok(clinicalAlertService.acknowledge(alertId));
    }

    /** US4 — Dismiss an alert with a reason. Roles: DOCTOR, NURSE, ADMIN. */
    @PatchMapping("/alerts/{alertId}/dismiss")
    public ResponseEntity<ClinicalAlertResponse> dismissAlert(
            @PathVariable UUID alertId,
            @Valid @RequestBody DismissAlertRequest request) {
        return ResponseEntity.ok(clinicalAlertService.dismiss(alertId, request.reason()));
    }
}

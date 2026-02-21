package com.ainexus.hospital.patient.controller;

import com.ainexus.hospital.patient.dto.DashboardStatsResponse;
import com.ainexus.hospital.patient.dto.PatientRiskRow;
import com.ainexus.hospital.patient.service.PatientRiskDashboardService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
public class PatientRiskDashboardController {

    private final PatientRiskDashboardService dashboardService;

    public PatientRiskDashboardController(PatientRiskDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /** US5 — Risk-ranked patient list. DOCTOR sees own patients; ADMIN sees all. */
    @GetMapping("/patient-risk")
    public ResponseEntity<Page<PatientRiskRow>> getPatientRisk(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(dashboardService.getRiskRankedPatients(PageRequest.of(page, size)));
    }

    /** US5 — System-wide clinical stats snapshot. Roles: DOCTOR, ADMIN. */
    @GetMapping("/stats")
    public ResponseEntity<DashboardStatsResponse> getStats() {
        return ResponseEntity.ok(dashboardService.getStats());
    }
}

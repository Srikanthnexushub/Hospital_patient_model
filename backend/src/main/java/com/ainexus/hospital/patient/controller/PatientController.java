package com.ainexus.hospital.patient.controller;

import com.ainexus.hospital.patient.dto.request.PatientRegistrationRequest;
import com.ainexus.hospital.patient.dto.request.PatientStatusChangeRequest;
import com.ainexus.hospital.patient.dto.request.PatientUpdateRequest;
import com.ainexus.hospital.patient.dto.response.*;
import com.ainexus.hospital.patient.service.PatientService;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * HTTP layer — no business logic. Delegates entirely to PatientService.
 * Role checks are enforced in the service layer (server-side).
 */
@RestController
@RequestMapping("/api/v1/patients")
public class PatientController {

    private final PatientService patientService;

    public PatientController(PatientService patientService) {
        this.patientService = patientService;
    }

    // ── US1: Register ─────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<PatientRegistrationResponse> registerPatient(
            @Valid @RequestBody PatientRegistrationRequest request) {
        setTrace("REGISTER_PATIENT");
        PatientRegistrationResponse response = patientService.registerPatient(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/check-phone")
    public ResponseEntity<DuplicatePhoneResponse> checkDuplicatePhone(
            @RequestParam String phone,
            @RequestParam(required = false) String excludePatientId) {
        setTrace("CHECK_DUPLICATE_PHONE");
        return ResponseEntity.ok(patientService.checkDuplicatePhone(phone, excludePatientId));
    }

    // ── US2: Search ────────────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<PagedResponse<PatientSummaryResponse>> searchPatients(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "ACTIVE") String status,
            @RequestParam(defaultValue = "ALL") String gender,
            @RequestParam(defaultValue = "ALL") String bloodGroup,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        setTrace("SEARCH_PATIENTS");
        return ResponseEntity.ok(patientService.searchPatients(query, status, gender, bloodGroup, page, size));
    }

    // ── US3: Profile ───────────────────────────────────────────────────────────

    @GetMapping("/{patientId}")
    public ResponseEntity<PatientResponse> getPatient(@PathVariable String patientId) {
        setTrace("GET_PATIENT");
        return ResponseEntity.ok(patientService.getPatient(patientId));
    }

    // ── US4: Update ────────────────────────────────────────────────────────────

    @PutMapping("/{patientId}")
    public ResponseEntity<PatientResponse> updatePatient(
            @PathVariable String patientId,
            @RequestHeader("If-Match") Integer version,
            @Valid @RequestBody PatientUpdateRequest request) {
        setTrace("UPDATE_PATIENT");
        return ResponseEntity.ok(patientService.updatePatient(patientId, request, version));
    }

    // ── US5: Status Management ──────────────────────────────────────────────────

    @PatchMapping("/{patientId}/status")
    public ResponseEntity<PatientStatusChangeResponse> changePatientStatus(
            @PathVariable String patientId,
            @Valid @RequestBody PatientStatusChangeRequest request) {
        setTrace("CHANGE_PATIENT_STATUS");
        return ResponseEntity.ok(patientService.changePatientStatus(patientId, request));
    }

    // ── Helper ──────────────────────────────────────────────────────────────────

    private void setTrace(String operation) {
        if (MDC.get("traceId") == null) {
            MDC.put("traceId", UUID.randomUUID().toString().substring(0, 16));
        }
        MDC.put("operation", operation);
    }
}

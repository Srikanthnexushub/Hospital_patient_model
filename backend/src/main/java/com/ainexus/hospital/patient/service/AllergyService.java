package com.ainexus.hospital.patient.service;

import com.ainexus.hospital.patient.audit.EmrAuditService;
import com.ainexus.hospital.patient.dto.request.RecordAllergyRequest;
import com.ainexus.hospital.patient.dto.response.AllergyResponse;
import com.ainexus.hospital.patient.entity.PatientAllergy;
import com.ainexus.hospital.patient.exception.ResourceNotFoundException;
import com.ainexus.hospital.patient.mapper.AllergyMapper;
import com.ainexus.hospital.patient.repository.AllergyRepository;
import com.ainexus.hospital.patient.repository.PatientRepository;
import com.ainexus.hospital.patient.security.AuthContext;
import com.ainexus.hospital.patient.security.RoleGuard;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class AllergyService {

    private final AllergyRepository allergyRepository;
    private final PatientRepository patientRepository;
    private final AllergyMapper allergyMapper;
    private final EmrAuditService emrAuditService;
    private final RoleGuard roleGuard;
    private final MeterRegistry meterRegistry;

    public AllergyService(AllergyRepository allergyRepository,
                          PatientRepository patientRepository,
                          AllergyMapper allergyMapper,
                          EmrAuditService emrAuditService,
                          RoleGuard roleGuard,
                          MeterRegistry meterRegistry) {
        this.allergyRepository = allergyRepository;
        this.patientRepository = patientRepository;
        this.allergyMapper = allergyMapper;
        this.emrAuditService = emrAuditService;
        this.roleGuard = roleGuard;
        this.meterRegistry = meterRegistry;
    }

    // ── US4: Record Allergy ───────────────────────────────────────────────────

    @Transactional
    public AllergyResponse recordAllergy(String patientId, RecordAllergyRequest request) {
        roleGuard.requireRoles("DOCTOR", "NURSE", "ADMIN");
        AuthContext ctx = AuthContext.Holder.get();

        if (!patientRepository.existsById(patientId)) {
            throw new ResourceNotFoundException("Patient not found: " + patientId);
        }

        if (request.onsetDate() != null && request.onsetDate().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Onset date cannot be in the future.");
        }

        PatientAllergy allergy = PatientAllergy.builder()
                .patientId(patientId)
                .substance(request.substance())
                .type(request.type())
                .severity(request.severity())
                .reaction(request.reaction())
                .onsetDate(request.onsetDate())
                .notes(request.notes())
                .createdBy(ctx.getUsername())
                .createdAt(OffsetDateTime.now())
                .build();

        allergyRepository.save(allergy);

        emrAuditService.writeAuditLog(
                "ALLERGY", allergy.getId().toString(), patientId,
                "CREATE", ctx.getUsername(), null);

        meterRegistry.counter("emr.allergies.recorded").increment();

        return allergyMapper.toResponse(allergy);
    }

    // ── US4: List Allergies ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AllergyResponse> listAllergies(String patientId) {
        roleGuard.requireRoles("DOCTOR", "NURSE", "ADMIN", "RECEPTIONIST");

        if (!patientRepository.existsById(patientId)) {
            throw new ResourceNotFoundException("Patient not found: " + patientId);
        }

        return allergyRepository.findByPatientIdAndActiveTrue(patientId)
                .stream().map(allergyMapper::toResponse).toList();
    }

    // ── US4: Delete (soft) Allergy ────────────────────────────────────────────

    @Transactional
    public void deleteAllergy(String patientId, UUID allergyId) {
        roleGuard.requireRoles("DOCTOR", "NURSE", "ADMIN");
        AuthContext ctx = AuthContext.Holder.get();

        if (!patientRepository.existsById(patientId)) {
            throw new ResourceNotFoundException("Patient not found: " + patientId);
        }

        PatientAllergy allergy = allergyRepository.findByIdAndPatientId(allergyId, patientId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Allergy not found: " + allergyId + " for patient: " + patientId));

        allergy.setActive(Boolean.FALSE);
        allergy.setUpdatedBy(ctx.getUsername());
        allergy.setUpdatedAt(OffsetDateTime.now());

        allergyRepository.save(allergy);

        emrAuditService.writeAuditLog(
                "ALLERGY", allergyId.toString(), patientId,
                "DELETE", ctx.getUsername(), null);
    }

    /** Used by MedicalSummaryService — no role check (caller already checked). */
    @Transactional(readOnly = true)
    public List<AllergyResponse> getActiveAllergies(String patientId) {
        return allergyRepository.findByPatientIdAndActiveTrue(patientId)
                .stream().map(allergyMapper::toResponse).toList();
    }
}

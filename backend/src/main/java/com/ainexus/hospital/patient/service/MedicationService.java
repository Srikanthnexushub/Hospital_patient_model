package com.ainexus.hospital.patient.service;

import com.ainexus.hospital.patient.audit.EmrAuditService;
import com.ainexus.hospital.patient.dto.request.PrescribeMedicationRequest;
import com.ainexus.hospital.patient.dto.request.UpdateMedicationRequest;
import com.ainexus.hospital.patient.dto.response.MedicationResponse;
import com.ainexus.hospital.patient.entity.MedicationStatus;
import com.ainexus.hospital.patient.entity.PatientMedication;
import com.ainexus.hospital.patient.exception.ResourceNotFoundException;
import com.ainexus.hospital.patient.mapper.MedicationMapper;
import com.ainexus.hospital.patient.repository.MedicationRepository;
import com.ainexus.hospital.patient.repository.PatientRepository;
import com.ainexus.hospital.patient.security.AuthContext;
import com.ainexus.hospital.patient.security.RoleGuard;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class MedicationService {

    private final MedicationRepository medicationRepository;
    private final PatientRepository patientRepository;
    private final MedicationMapper medicationMapper;
    private final EmrAuditService emrAuditService;
    private final RoleGuard roleGuard;
    private final MeterRegistry meterRegistry;

    public MedicationService(MedicationRepository medicationRepository,
                             PatientRepository patientRepository,
                             MedicationMapper medicationMapper,
                             EmrAuditService emrAuditService,
                             RoleGuard roleGuard,
                             MeterRegistry meterRegistry) {
        this.medicationRepository = medicationRepository;
        this.patientRepository = patientRepository;
        this.medicationMapper = medicationMapper;
        this.emrAuditService = emrAuditService;
        this.roleGuard = roleGuard;
        this.meterRegistry = meterRegistry;
    }

    // ── US3: Prescribe Medication ─────────────────────────────────────────────

    @Transactional
    public MedicationResponse prescribeMedication(String patientId, PrescribeMedicationRequest request) {
        roleGuard.requireRoles("DOCTOR", "ADMIN");
        AuthContext ctx = AuthContext.Holder.get();

        if (!patientRepository.existsById(patientId)) {
            throw new ResourceNotFoundException("Patient not found: " + patientId);
        }

        if (request.endDate() != null && request.endDate().isBefore(request.startDate())) {
            throw new IllegalArgumentException("End date cannot be before start date.");
        }

        PatientMedication medication = PatientMedication.builder()
                .patientId(patientId)
                .medicationName(request.medicationName())
                .genericName(request.genericName())
                .dosage(request.dosage())
                .frequency(request.frequency())
                .route(request.route())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .indication(request.indication())
                .prescribedBy(ctx.getUsername())
                .notes(request.notes())
                .createdAt(OffsetDateTime.now())
                .build();

        medicationRepository.save(medication);

        emrAuditService.writeAuditLog(
                "MEDICATION", medication.getId().toString(), patientId,
                "PRESCRIBE", ctx.getUsername(), null);

        meterRegistry.counter("emr.medications.prescribed").increment();

        return medicationMapper.toResponse(medication);
    }

    // ── US3: List Medications ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<MedicationResponse> listMedications(String patientId, String statusParam) {
        roleGuard.requireRoles("DOCTOR", "NURSE", "ADMIN");

        if (!patientRepository.existsById(patientId)) {
            throw new ResourceNotFoundException("Patient not found: " + patientId);
        }

        List<PatientMedication> medications;
        if ("ALL".equalsIgnoreCase(statusParam)) {
            medications = medicationRepository.findByPatientId(patientId);
        } else {
            medications = medicationRepository.findByPatientIdAndStatus(patientId, MedicationStatus.ACTIVE);
        }

        return medications.stream().map(medicationMapper::toResponse).toList();
    }

    // ── US3: Update Medication ────────────────────────────────────────────────

    @Transactional
    public MedicationResponse updateMedication(String patientId, UUID medicationId, UpdateMedicationRequest request) {
        roleGuard.requireRoles("DOCTOR", "ADMIN");
        AuthContext ctx = AuthContext.Holder.get();

        if (!patientRepository.existsById(patientId)) {
            throw new ResourceNotFoundException("Patient not found: " + patientId);
        }

        PatientMedication medication = medicationRepository.findByIdAndPatientId(medicationId, patientId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Medication not found: " + medicationId + " for patient: " + patientId));

        if (request.endDate() != null && request.endDate().isBefore(medication.getStartDate())) {
            throw new IllegalArgumentException("End date cannot be before start date.");
        }

        // Partial update — only apply non-null fields
        if (request.medicationName() != null) medication.setMedicationName(request.medicationName());
        if (request.genericName() != null) medication.setGenericName(request.genericName());
        if (request.dosage() != null) medication.setDosage(request.dosage());
        if (request.frequency() != null) medication.setFrequency(request.frequency());
        if (request.route() != null) medication.setRoute(request.route());
        if (request.endDate() != null) medication.setEndDate(request.endDate());
        if (request.indication() != null) medication.setIndication(request.indication());
        if (request.status() != null) medication.setStatus(request.status());
        if (request.notes() != null) medication.setNotes(request.notes());

        medication.setUpdatedAt(OffsetDateTime.now());

        medicationRepository.save(medication);

        String action = (request.status() != null && MedicationStatus.DISCONTINUED.equals(request.status()))
                ? "DISCONTINUE" : "UPDATE";
        emrAuditService.writeAuditLog(
                "MEDICATION", medication.getId().toString(), patientId,
                action, ctx.getUsername(), null);

        return medicationMapper.toResponse(medication);
    }

    /** Used by MedicalSummaryService — no role check (caller already checked). */
    @Transactional(readOnly = true)
    public List<MedicationResponse> getActiveMedications(String patientId) {
        return medicationRepository.findByPatientIdAndStatus(patientId, MedicationStatus.ACTIVE)
                .stream().map(medicationMapper::toResponse).toList();
    }
}

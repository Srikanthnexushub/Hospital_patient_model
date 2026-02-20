package com.ainexus.hospital.patient.service;

import com.ainexus.hospital.patient.audit.EmrAuditService;
import com.ainexus.hospital.patient.dto.request.CreateProblemRequest;
import com.ainexus.hospital.patient.dto.request.UpdateProblemRequest;
import com.ainexus.hospital.patient.dto.response.ProblemResponse;
import com.ainexus.hospital.patient.entity.PatientProblem;
import com.ainexus.hospital.patient.entity.ProblemStatus;
import com.ainexus.hospital.patient.exception.ResourceNotFoundException;
import com.ainexus.hospital.patient.mapper.ProblemMapper;
import com.ainexus.hospital.patient.repository.PatientRepository;
import com.ainexus.hospital.patient.repository.ProblemRepository;
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
public class ProblemService {

    private final ProblemRepository problemRepository;
    private final PatientRepository patientRepository;
    private final ProblemMapper problemMapper;
    private final EmrAuditService emrAuditService;
    private final RoleGuard roleGuard;
    private final MeterRegistry meterRegistry;

    public ProblemService(ProblemRepository problemRepository,
                          PatientRepository patientRepository,
                          ProblemMapper problemMapper,
                          EmrAuditService emrAuditService,
                          RoleGuard roleGuard,
                          MeterRegistry meterRegistry) {
        this.problemRepository = problemRepository;
        this.patientRepository = patientRepository;
        this.problemMapper = problemMapper;
        this.emrAuditService = emrAuditService;
        this.roleGuard = roleGuard;
        this.meterRegistry = meterRegistry;
    }

    // ── US2: Create Problem ───────────────────────────────────────────────────

    @Transactional
    public ProblemResponse createProblem(String patientId, CreateProblemRequest request) {
        roleGuard.requireRoles("DOCTOR", "ADMIN");
        AuthContext ctx = AuthContext.Holder.get();

        if (!patientRepository.existsById(patientId)) {
            throw new ResourceNotFoundException("Patient not found: " + patientId);
        }

        if (request.onsetDate() != null && request.onsetDate().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Onset date cannot be in the future.");
        }

        PatientProblem problem = PatientProblem.builder()
                .patientId(patientId)
                .title(request.title())
                .description(request.description())
                .icdCode(request.icdCode())
                .severity(request.severity())
                .status(request.status() != null ? request.status() : ProblemStatus.ACTIVE)
                .onsetDate(request.onsetDate())
                .notes(request.notes())
                .createdBy(ctx.getUsername())
                .createdAt(OffsetDateTime.now())
                .build();

        problemRepository.save(problem);

        emrAuditService.writeAuditLog(
                "PROBLEM", problem.getId().toString(), patientId,
                "CREATE", ctx.getUsername(), null);

        meterRegistry.counter("emr.problems.created").increment();

        return problemMapper.toResponse(problem);
    }

    // ── US2: List Problems ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ProblemResponse> listProblems(String patientId, String statusParam) {
        roleGuard.requireRoles("DOCTOR", "NURSE", "ADMIN");

        if (!patientRepository.existsById(patientId)) {
            throw new ResourceNotFoundException("Patient not found: " + patientId);
        }

        List<PatientProblem> problems;
        if ("ALL".equalsIgnoreCase(statusParam)) {
            problems = problemRepository.findByPatientId(patientId);
        } else {
            ProblemStatus status = statusParam != null
                    ? ProblemStatus.fromValue(statusParam)
                    : ProblemStatus.ACTIVE;
            if (status == null) status = ProblemStatus.ACTIVE;
            problems = problemRepository.findByPatientIdAndStatus(patientId, status);
        }

        return problems.stream().map(problemMapper::toResponse).toList();
    }

    // ── US2: Update Problem ───────────────────────────────────────────────────

    @Transactional
    public ProblemResponse updateProblem(String patientId, UUID problemId, UpdateProblemRequest request) {
        roleGuard.requireRoles("DOCTOR", "ADMIN");
        AuthContext ctx = AuthContext.Holder.get();

        if (!patientRepository.existsById(patientId)) {
            throw new ResourceNotFoundException("Patient not found: " + patientId);
        }

        PatientProblem problem = problemRepository.findByIdAndPatientId(problemId, patientId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Problem not found: " + problemId + " for patient: " + patientId));

        if (request.onsetDate() != null && request.onsetDate().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Onset date cannot be in the future.");
        }

        // Partial update — only apply non-null fields
        if (request.title() != null) problem.setTitle(request.title());
        if (request.description() != null) problem.setDescription(request.description());
        if (request.icdCode() != null) problem.setIcdCode(request.icdCode());
        if (request.severity() != null) problem.setSeverity(request.severity());
        if (request.status() != null) problem.setStatus(request.status());
        if (request.onsetDate() != null) problem.setOnsetDate(request.onsetDate());
        if (request.notes() != null) problem.setNotes(request.notes());

        problem.setUpdatedBy(ctx.getUsername());
        problem.setUpdatedAt(OffsetDateTime.now());

        problemRepository.save(problem);

        String action = (request.status() != null && ProblemStatus.RESOLVED.equals(request.status()))
                ? "RESOLVE" : "UPDATE";
        emrAuditService.writeAuditLog(
                "PROBLEM", problem.getId().toString(), patientId,
                action, ctx.getUsername(), null);

        return problemMapper.toResponse(problem);
    }

    /** Used by MedicalSummaryService — no role check (caller already checked). */
    @Transactional(readOnly = true)
    public List<ProblemResponse> getActiveProblems(String patientId) {
        return problemRepository.findByPatientIdAndStatus(patientId, ProblemStatus.ACTIVE)
                .stream().map(problemMapper::toResponse).toList();
    }
}

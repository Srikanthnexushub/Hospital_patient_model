package com.ainexus.hospital.patient.unit.service;

import com.ainexus.hospital.patient.audit.EmrAuditService;
import com.ainexus.hospital.patient.dto.request.CreateProblemRequest;
import com.ainexus.hospital.patient.dto.request.UpdateProblemRequest;
import com.ainexus.hospital.patient.dto.response.ProblemResponse;
import com.ainexus.hospital.patient.entity.PatientProblem;
import com.ainexus.hospital.patient.entity.ProblemSeverity;
import com.ainexus.hospital.patient.entity.ProblemStatus;
import com.ainexus.hospital.patient.exception.ForbiddenException;
import com.ainexus.hospital.patient.exception.ResourceNotFoundException;
import com.ainexus.hospital.patient.mapper.ProblemMapper;
import com.ainexus.hospital.patient.repository.PatientRepository;
import com.ainexus.hospital.patient.repository.ProblemRepository;
import com.ainexus.hospital.patient.security.AuthContext;
import com.ainexus.hospital.patient.security.RoleGuard;
import com.ainexus.hospital.patient.service.ProblemService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProblemServiceTest {

    @Mock private ProblemRepository problemRepository;
    @Mock private PatientRepository patientRepository;
    @Mock private EmrAuditService emrAuditService;

    private ProblemService problemService;

    private static final String PATIENT_ID = "P2025001";
    private static final UUID   PROBLEM_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        problemService = new ProblemService(
                problemRepository, patientRepository,
                new ProblemMapper(), emrAuditService,
                new RoleGuard(), new SimpleMeterRegistry());
    }

    @AfterEach
    void clearContext() {
        AuthContext.Holder.clear();
    }

    private void setAuth(String role) {
        AuthContext.Holder.set(new AuthContext("uid001", role.toLowerCase() + "1", role));
    }

    private PatientProblem stubProblem() {
        PatientProblem p = new PatientProblem();
        p.setId(PROBLEM_ID);
        p.setPatientId(PATIENT_ID);
        p.setTitle("Hypertension");
        p.setSeverity(ProblemSeverity.MODERATE);
        p.setStatus(ProblemStatus.ACTIVE);
        p.setCreatedBy("doctor1");
        p.setCreatedAt(OffsetDateTime.now());
        return p;
    }

    // ── createProblem ─────────────────────────────────────────────────────────

    @Test
    void createProblem_doctorRole_createsAndAudits() {
        setAuth("DOCTOR");
        when(patientRepository.existsById(PATIENT_ID)).thenReturn(true);
        when(problemRepository.save(any())).thenAnswer(inv -> {
            PatientProblem p = inv.getArgument(0);
            p.setId(PROBLEM_ID);
            return p;
        });

        CreateProblemRequest request = new CreateProblemRequest(
                "Hypertension", null, null,
                ProblemSeverity.MODERATE, ProblemStatus.ACTIVE, null, null);

        ProblemResponse response = problemService.createProblem(PATIENT_ID, request);

        assertThat(response.title()).isEqualTo("Hypertension");
        assertThat(response.severity()).isEqualTo(ProblemSeverity.MODERATE);
        verify(emrAuditService).writeAuditLog(eq("PROBLEM"), any(), eq(PATIENT_ID),
                eq("CREATE"), anyString(), isNull());
    }

    @Test
    void createProblem_onsetDateInFuture_throws400() {
        setAuth("DOCTOR");
        when(patientRepository.existsById(PATIENT_ID)).thenReturn(true);

        CreateProblemRequest request = new CreateProblemRequest(
                "Hypertension", null, null,
                ProblemSeverity.MILD, ProblemStatus.ACTIVE,
                LocalDate.now().plusDays(1), null);

        assertThatThrownBy(() -> problemService.createProblem(PATIENT_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("future");
    }

    @Test
    void createProblem_patientNotFound_throws404() {
        setAuth("DOCTOR");
        when(patientRepository.existsById(PATIENT_ID)).thenReturn(false);

        CreateProblemRequest request = new CreateProblemRequest(
                "Hypertension", null, null,
                ProblemSeverity.MILD, ProblemStatus.ACTIVE, null, null);

        assertThatThrownBy(() -> problemService.createProblem(PATIENT_ID, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createProblem_nurseRole_throwsForbidden() {
        setAuth("NURSE");

        CreateProblemRequest request = new CreateProblemRequest(
                "Hypertension", null, null,
                ProblemSeverity.MILD, ProblemStatus.ACTIVE, null, null);

        assertThatThrownBy(() -> problemService.createProblem(PATIENT_ID, request))
                .isInstanceOf(ForbiddenException.class);
    }

    // ── listProblems ──────────────────────────────────────────────────────────

    @Test
    void listProblems_activeFilter_returnsOnlyActive() {
        setAuth("NURSE");
        when(patientRepository.existsById(PATIENT_ID)).thenReturn(true);
        when(problemRepository.findByPatientIdAndStatus(PATIENT_ID, ProblemStatus.ACTIVE))
                .thenReturn(List.of(stubProblem()));

        List<ProblemResponse> result = problemService.listProblems(PATIENT_ID, "ACTIVE");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).status()).isEqualTo(ProblemStatus.ACTIVE);
    }

    @Test
    void listProblems_allFilter_returnsAll() {
        setAuth("DOCTOR");
        PatientProblem resolved = stubProblem();
        resolved.setStatus(ProblemStatus.RESOLVED);
        when(patientRepository.existsById(PATIENT_ID)).thenReturn(true);
        when(problemRepository.findByPatientId(PATIENT_ID))
                .thenReturn(List.of(stubProblem(), resolved));

        List<ProblemResponse> result = problemService.listProblems(PATIENT_ID, "ALL");

        assertThat(result).hasSize(2);
    }

    // ── updateProblem ─────────────────────────────────────────────────────────

    @Test
    void updateProblem_resolve_auditsResolve() {
        setAuth("DOCTOR");
        when(patientRepository.existsById(PATIENT_ID)).thenReturn(true);
        when(problemRepository.findByIdAndPatientId(PROBLEM_ID, PATIENT_ID))
                .thenReturn(Optional.of(stubProblem()));
        when(problemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateProblemRequest request = new UpdateProblemRequest(
                null, null, null, null, ProblemStatus.RESOLVED, null, null);

        ProblemResponse response = problemService.updateProblem(PATIENT_ID, PROBLEM_ID, request);

        assertThat(response.status()).isEqualTo(ProblemStatus.RESOLVED);
        verify(emrAuditService).writeAuditLog(eq("PROBLEM"), any(), eq(PATIENT_ID),
                eq("RESOLVE"), anyString(), isNull());
    }

    @Test
    void updateProblem_problemNotFound_throws404() {
        setAuth("DOCTOR");
        when(patientRepository.existsById(PATIENT_ID)).thenReturn(true);
        when(problemRepository.findByIdAndPatientId(PROBLEM_ID, PATIENT_ID))
                .thenReturn(Optional.empty());

        UpdateProblemRequest request = new UpdateProblemRequest(
                "Updated", null, null, null, null, null, null);

        assertThatThrownBy(() -> problemService.updateProblem(PATIENT_ID, PROBLEM_ID, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}

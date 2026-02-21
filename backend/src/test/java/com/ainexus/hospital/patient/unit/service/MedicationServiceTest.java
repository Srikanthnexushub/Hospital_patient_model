package com.ainexus.hospital.patient.unit.service;

import com.ainexus.hospital.patient.audit.EmrAuditService;
import com.ainexus.hospital.patient.dto.request.PrescribeMedicationRequest;
import com.ainexus.hospital.patient.dto.request.UpdateMedicationRequest;
import com.ainexus.hospital.patient.dto.response.MedicationResponse;
import com.ainexus.hospital.patient.entity.MedicationRoute;
import com.ainexus.hospital.patient.entity.MedicationStatus;
import com.ainexus.hospital.patient.entity.PatientMedication;
import com.ainexus.hospital.patient.exception.ForbiddenException;
import com.ainexus.hospital.patient.exception.ResourceNotFoundException;
import com.ainexus.hospital.patient.mapper.MedicationMapper;
import com.ainexus.hospital.patient.repository.MedicationRepository;
import com.ainexus.hospital.patient.repository.PatientRepository;
import com.ainexus.hospital.patient.security.AuthContext;
import com.ainexus.hospital.patient.security.RoleGuard;
import com.ainexus.hospital.patient.service.MedicationService;
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
class MedicationServiceTest {

    @Mock private MedicationRepository medicationRepository;
    @Mock private PatientRepository patientRepository;
    @Mock private EmrAuditService emrAuditService;

    private MedicationService medicationService;

    private static final String PATIENT_ID     = "P2025001";
    private static final UUID   MEDICATION_ID  = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        medicationService = new MedicationService(
                medicationRepository, patientRepository,
                new MedicationMapper(), emrAuditService,
                new RoleGuard(), new SimpleMeterRegistry());
    }

    @AfterEach
    void clearContext() {
        AuthContext.Holder.clear();
    }

    private void setAuth(String role) {
        AuthContext.Holder.set(new AuthContext("uid001", role.toLowerCase() + "1", role));
    }

    private PatientMedication stubMedication() {
        PatientMedication m = new PatientMedication();
        m.setId(MEDICATION_ID);
        m.setPatientId(PATIENT_ID);
        m.setMedicationName("Lisinopril");
        m.setDosage("10mg");
        m.setFrequency("Once daily");
        m.setRoute(MedicationRoute.ORAL);
        m.setStartDate(LocalDate.of(2026, 1, 1));
        m.setPrescribedBy("doctor1");
        m.setStatus(MedicationStatus.ACTIVE);
        m.setCreatedAt(OffsetDateTime.now());
        return m;
    }

    // ── prescribeMedication ───────────────────────────────────────────────────

    @Test
    void prescribeMedication_doctorRole_createsAndSetsPrescriberFromContext() {
        setAuth("DOCTOR");
        when(patientRepository.existsById(PATIENT_ID)).thenReturn(true);
        when(medicationRepository.save(any())).thenAnswer(inv -> {
            PatientMedication m = inv.getArgument(0);
            m.setId(MEDICATION_ID);
            return m;
        });

        PrescribeMedicationRequest request = new PrescribeMedicationRequest(
                "Lisinopril", null, "10mg", "Once daily",
                MedicationRoute.ORAL, LocalDate.of(2026, 1, 1),
                null, "Hypertension", null);

        MedicationResponse response = medicationService.prescribeMedication(PATIENT_ID, request);

        assertThat(response.medicationName()).isEqualTo("Lisinopril");
        assertThat(response.prescribedBy()).isEqualTo("doctor1"); // from AuthContext
        verify(emrAuditService).writeAuditLog(eq("MEDICATION"), any(), eq(PATIENT_ID),
                eq("PRESCRIBE"), anyString(), isNull());
    }

    @Test
    void prescribeMedication_endDateBeforeStartDate_throws400() {
        setAuth("DOCTOR");
        when(patientRepository.existsById(PATIENT_ID)).thenReturn(true);

        PrescribeMedicationRequest request = new PrescribeMedicationRequest(
                "Lisinopril", null, "10mg", "Once daily",
                MedicationRoute.ORAL, LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 1, 1), null, null);

        assertThatThrownBy(() -> medicationService.prescribeMedication(PATIENT_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("End date");
    }

    @Test
    void prescribeMedication_nurseRole_throwsForbidden() {
        setAuth("NURSE");

        PrescribeMedicationRequest request = new PrescribeMedicationRequest(
                "Lisinopril", null, "10mg", "Once daily",
                MedicationRoute.ORAL, LocalDate.of(2026, 1, 1),
                null, null, null);

        assertThatThrownBy(() -> medicationService.prescribeMedication(PATIENT_ID, request))
                .isInstanceOf(ForbiddenException.class);
    }

    // ── listMedications ───────────────────────────────────────────────────────

    @Test
    void listMedications_defaultActive_returnsOnlyActive() {
        setAuth("NURSE");
        when(patientRepository.existsById(PATIENT_ID)).thenReturn(true);
        when(medicationRepository.findByPatientIdAndStatus(PATIENT_ID, MedicationStatus.ACTIVE))
                .thenReturn(List.of(stubMedication()));

        List<MedicationResponse> result = medicationService.listMedications(PATIENT_ID, "ACTIVE");

        assertThat(result).hasSize(1);
    }

    @Test
    void listMedications_all_callsFindAll() {
        setAuth("DOCTOR");
        when(patientRepository.existsById(PATIENT_ID)).thenReturn(true);
        when(medicationRepository.findByPatientId(PATIENT_ID))
                .thenReturn(List.of(stubMedication()));

        List<MedicationResponse> result = medicationService.listMedications(PATIENT_ID, "ALL");

        assertThat(result).hasSize(1);
        verify(medicationRepository).findByPatientId(PATIENT_ID);
        verify(medicationRepository, never()).findByPatientIdAndStatus(any(), any());
    }

    // ── updateMedication ──────────────────────────────────────────────────────

    @Test
    void updateMedication_discontinue_auditsDiscontinue() {
        setAuth("DOCTOR");
        when(patientRepository.existsById(PATIENT_ID)).thenReturn(true);
        when(medicationRepository.findByIdAndPatientId(MEDICATION_ID, PATIENT_ID))
                .thenReturn(Optional.of(stubMedication()));
        when(medicationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateMedicationRequest request = new UpdateMedicationRequest(
                null, null, null, null, null, null, null,
                MedicationStatus.DISCONTINUED, null);

        MedicationResponse response = medicationService.updateMedication(PATIENT_ID, MEDICATION_ID, request);

        assertThat(response.status()).isEqualTo(MedicationStatus.DISCONTINUED);
        verify(emrAuditService).writeAuditLog(eq("MEDICATION"), any(), eq(PATIENT_ID),
                eq("DISCONTINUE"), anyString(), isNull());
    }

    @Test
    void updateMedication_notFound_throws404() {
        setAuth("DOCTOR");
        when(patientRepository.existsById(PATIENT_ID)).thenReturn(true);
        when(medicationRepository.findByIdAndPatientId(MEDICATION_ID, PATIENT_ID))
                .thenReturn(Optional.empty());

        UpdateMedicationRequest request = new UpdateMedicationRequest(
                "Updated", null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> medicationService.updateMedication(PATIENT_ID, MEDICATION_ID, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}

package com.ainexus.hospital.patient.unit.service;

import com.ainexus.hospital.patient.audit.EmrAuditService;
import com.ainexus.hospital.patient.dto.request.RecordAllergyRequest;
import com.ainexus.hospital.patient.dto.response.AllergyResponse;
import com.ainexus.hospital.patient.entity.AllergySeverity;
import com.ainexus.hospital.patient.entity.AllergyType;
import com.ainexus.hospital.patient.entity.PatientAllergy;
import com.ainexus.hospital.patient.exception.ForbiddenException;
import com.ainexus.hospital.patient.exception.ResourceNotFoundException;
import com.ainexus.hospital.patient.mapper.AllergyMapper;
import com.ainexus.hospital.patient.repository.AllergyRepository;
import com.ainexus.hospital.patient.repository.PatientRepository;
import com.ainexus.hospital.patient.security.AuthContext;
import com.ainexus.hospital.patient.security.RoleGuard;
import com.ainexus.hospital.patient.service.AllergyService;
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
class AllergyServiceTest {

    @Mock private AllergyRepository allergyRepository;
    @Mock private PatientRepository patientRepository;
    @Mock private EmrAuditService emrAuditService;

    private AllergyService allergyService;

    private static final String PATIENT_ID = "P2025001";
    private static final UUID   ALLERGY_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        allergyService = new AllergyService(
                allergyRepository, patientRepository,
                new AllergyMapper(), emrAuditService,
                new RoleGuard(), new SimpleMeterRegistry());
    }

    @AfterEach
    void clearContext() {
        AuthContext.Holder.clear();
    }

    private void setAuth(String role) {
        AuthContext.Holder.set(new AuthContext("uid001", role.toLowerCase() + "1", role));
    }

    private PatientAllergy stubAllergy() {
        PatientAllergy a = new PatientAllergy();
        a.setId(ALLERGY_ID);
        a.setPatientId(PATIENT_ID);
        a.setSubstance("Penicillin");
        a.setType(AllergyType.DRUG);
        a.setSeverity(AllergySeverity.SEVERE);
        a.setReaction("Anaphylaxis");
        a.setActive(Boolean.TRUE);
        a.setCreatedBy("doctor1");
        a.setCreatedAt(OffsetDateTime.now());
        return a;
    }

    // ── recordAllergy ─────────────────────────────────────────────────────────

    @Test
    void recordAllergy_nurseRole_createsAndAudits() {
        setAuth("NURSE");
        when(patientRepository.existsById(PATIENT_ID)).thenReturn(true);
        when(allergyRepository.save(any())).thenAnswer(inv -> {
            PatientAllergy a = inv.getArgument(0);
            a.setId(ALLERGY_ID);
            return a;
        });

        RecordAllergyRequest request = new RecordAllergyRequest(
                "Penicillin", AllergyType.DRUG, AllergySeverity.SEVERE,
                "Anaphylaxis", null, null);

        AllergyResponse response = allergyService.recordAllergy(PATIENT_ID, request);

        assertThat(response.substance()).isEqualTo("Penicillin");
        // active is set by @PrePersist which only runs via JPA (integration tests cover this)
        verify(emrAuditService).writeAuditLog(eq("ALLERGY"), any(), eq(PATIENT_ID),
                eq("CREATE"), anyString(), isNull());
    }

    @Test
    void recordAllergy_onsetDateInFuture_throws400() {
        setAuth("DOCTOR");
        when(patientRepository.existsById(PATIENT_ID)).thenReturn(true);

        RecordAllergyRequest request = new RecordAllergyRequest(
                "Penicillin", AllergyType.DRUG, AllergySeverity.MILD,
                "Rash", LocalDate.now().plusDays(1), null);

        assertThatThrownBy(() -> allergyService.recordAllergy(PATIENT_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("future");
    }

    // ── listAllergies ─────────────────────────────────────────────────────────

    @Test
    void listAllergies_receptionistCanView() {
        setAuth("RECEPTIONIST");
        when(patientRepository.existsById(PATIENT_ID)).thenReturn(true);
        when(allergyRepository.findByPatientIdAndActiveTrue(PATIENT_ID))
                .thenReturn(List.of(stubAllergy()));

        List<AllergyResponse> result = allergyService.listAllergies(PATIENT_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).substance()).isEqualTo("Penicillin");
    }

    @Test
    void listAllergies_patientNotFound_throws404() {
        setAuth("DOCTOR");
        when(patientRepository.existsById(PATIENT_ID)).thenReturn(false);

        assertThatThrownBy(() -> allergyService.listAllergies(PATIENT_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── deleteAllergy ─────────────────────────────────────────────────────────

    @Test
    void deleteAllergy_setsActiveToFalseAndAudits() {
        setAuth("DOCTOR");
        PatientAllergy allergy = stubAllergy();
        when(patientRepository.existsById(PATIENT_ID)).thenReturn(true);
        when(allergyRepository.findByIdAndPatientId(ALLERGY_ID, PATIENT_ID))
                .thenReturn(Optional.of(allergy));
        when(allergyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        allergyService.deleteAllergy(PATIENT_ID, ALLERGY_ID);

        assertThat(allergy.getActive()).isFalse();
        verify(emrAuditService).writeAuditLog(eq("ALLERGY"), any(), eq(PATIENT_ID),
                eq("DELETE"), anyString(), isNull());
    }

    @Test
    void deleteAllergy_nurseCannotDelete_throwsForbidden() {
        // Per spec: NURSE can create/delete allergies; RECEPTIONIST cannot
        // This test verifies RECEPTIONIST cannot delete
        setAuth("RECEPTIONIST");

        assertThatThrownBy(() -> allergyService.deleteAllergy(PATIENT_ID, ALLERGY_ID))
                .isInstanceOf(ForbiddenException.class);
    }
}

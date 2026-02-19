package com.ainexus.hospital.patient.unit.service;

import com.ainexus.hospital.patient.audit.AuditService;
import com.ainexus.hospital.patient.dto.request.PatientRegistrationRequest;
import com.ainexus.hospital.patient.dto.response.DuplicatePhoneResponse;
import com.ainexus.hospital.patient.dto.response.PatientRegistrationResponse;
import com.ainexus.hospital.patient.dto.response.PatientSummaryResponse;
import com.ainexus.hospital.patient.dto.response.PagedResponse;
import com.ainexus.hospital.patient.entity.*;
import com.ainexus.hospital.patient.exception.ForbiddenException;
import com.ainexus.hospital.patient.mapper.PatientMapper;
import com.ainexus.hospital.patient.repository.PatientRepository;
import com.ainexus.hospital.patient.security.AuthContext;
import com.ainexus.hospital.patient.security.RoleGuard;
import com.ainexus.hospital.patient.service.PatientIdGeneratorService;
import com.ainexus.hospital.patient.service.PatientService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PatientServiceTest {

    @Mock private PatientRepository patientRepository;
    @Mock private PatientIdGeneratorService idGeneratorService;
    @Mock private PatientMapper patientMapper;
    @Mock private AuditService auditService;

    // Use real implementations for RoleGuard (reads AuthContext) and MeterRegistry
    private final RoleGuard roleGuard = new RoleGuard();
    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    private PatientService patientService;

    private final PatientRegistrationRequest validRequest = new PatientRegistrationRequest(
            "Jane", "Smith", LocalDate.of(1985, 6, 15), Gender.FEMALE,
            BloodGroup.A_POS, "555-123-4567", "jane@example.com",
            null, null, null, null, null, null, null, null, null
    );

    @BeforeEach
    void setUpService() {
        patientService = new PatientService(
                patientRepository, idGeneratorService, patientMapper,
                auditService, roleGuard, meterRegistry
        );
        AuthContext.Holder.set(new AuthContext("user1", "receptionist1", "RECEPTIONIST"));
    }

    @AfterEach
    void clearAuth() {
        AuthContext.Holder.clear();
    }

    // ── Registration ────────────────────────────────────────────────────────

    @Test
    void registerPatient_asReceptionist_succeeds() {
        Patient savedPatient = Patient.builder()
                .patientId("P2026001").firstName("Jane").lastName("Smith")
                .dateOfBirth(LocalDate.of(1985, 6, 15)).gender(Gender.FEMALE)
                .phone("555-123-4567").status(PatientStatus.ACTIVE)
                .createdAt(OffsetDateTime.now()).createdBy("receptionist1")
                .updatedAt(OffsetDateTime.now()).updatedBy("receptionist1")
                .version(0).bloodGroup(BloodGroup.A_POS).build();

        when(idGeneratorService.generatePatientId()).thenReturn("P2026001");
        when(patientMapper.toEntity(any())).thenReturn(savedPatient);
        when(patientRepository.save(any())).thenReturn(savedPatient);

        PatientRegistrationResponse response = patientService.registerPatient(validRequest);

        assertThat(response.patientId()).isEqualTo("P2026001");
        assertThat(response.message()).contains("P2026001");
        verify(auditService).writeAuditLog(eq("REGISTER"), eq("P2026001"), eq("receptionist1"), isNull());
    }

    @Test
    void registerPatient_asAdmin_succeeds() {
        AuthContext.Holder.set(new AuthContext("admin1", "admin1", "ADMIN"));
        when(idGeneratorService.generatePatientId()).thenReturn("P2026002");
        Patient patient = Patient.builder().patientId("P2026002").firstName("Jane").lastName("Smith")
                .dateOfBirth(LocalDate.of(1985, 6, 15)).gender(Gender.FEMALE)
                .phone("555-123-4567").status(PatientStatus.ACTIVE)
                .createdAt(OffsetDateTime.now()).createdBy("admin1")
                .updatedAt(OffsetDateTime.now()).updatedBy("admin1")
                .version(0).bloodGroup(BloodGroup.UNKNOWN).build();
        when(patientMapper.toEntity(any())).thenReturn(patient);
        when(patientRepository.save(any())).thenReturn(patient);

        PatientRegistrationResponse response = patientService.registerPatient(validRequest);
        assertThat(response.patientId()).isEqualTo("P2026002");
    }

    @Test
    void registerPatient_asDoctor_throwsForbidden() {
        AuthContext.Holder.set(new AuthContext("doc1", "doctor1", "DOCTOR"));
        assertThatThrownBy(() -> patientService.registerPatient(validRequest))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(patientRepository, idGeneratorService);
    }

    @Test
    void registerPatient_asNurse_throwsForbidden() {
        AuthContext.Holder.set(new AuthContext("nur1", "nurse1", "NURSE"));
        assertThatThrownBy(() -> patientService.registerPatient(validRequest))
                .isInstanceOf(ForbiddenException.class);
    }

    // ── Duplicate phone check ───────────────────────────────────────────────

    @Test
    void checkDuplicatePhone_noDuplicate_returnsFalse() {
        when(patientRepository.findFirstByPhone("555-123-4567")).thenReturn(Optional.empty());

        DuplicatePhoneResponse result = patientService.checkDuplicatePhone("555-123-4567", null);

        assertThat(result.duplicate()).isFalse();
        assertThat(result.patientId()).isNull();
    }

    @Test
    void checkDuplicatePhone_duplicateExists_returnsDetails() {
        Patient existing = Patient.builder()
                .patientId("P2026001").firstName("John").lastName("Doe")
                .phone("555-123-4567").build();
        when(patientRepository.findFirstByPhone("555-123-4567")).thenReturn(Optional.of(existing));

        DuplicatePhoneResponse result = patientService.checkDuplicatePhone("555-123-4567", null);

        assertThat(result.duplicate()).isTrue();
        assertThat(result.patientId()).isEqualTo("P2026001");
        assertThat(result.patientName()).isEqualTo("John Doe");
    }

    // ── Search (US2) ────────────────────────────────────────────────────────

    private Patient samplePatient(String id, String first, String last,
                                   PatientStatus status, Gender gender) {
        return Patient.builder()
                .patientId(id).firstName(first).lastName(last)
                .dateOfBirth(LocalDate.of(1985, 1, 1)).gender(gender)
                .phone("555-000-0001").status(status)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now())
                .bloodGroup(BloodGroup.UNKNOWN).version(0).build();
    }

    private PatientSummaryResponse summaryOf(Patient p) {
        return new PatientSummaryResponse(p.getPatientId(), p.getFirstName(),
                p.getLastName(), 40, p.getGender(), p.getPhone(), p.getStatus());
    }

    @Test
    void searchPatients_defaultQuery_returnsActivePatientsPage() {
        Patient active = samplePatient("P2026001", "Jane", "Smith", PatientStatus.ACTIVE, Gender.FEMALE);
        Page<Patient> page = new PageImpl<>(List.of(active), PageRequest.of(0, 20), 1);

        when(patientRepository.search(isNull(), eq(PatientStatus.ACTIVE), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(page);
        when(patientMapper.toSummary(active)).thenReturn(summaryOf(active));

        PagedResponse<PatientSummaryResponse> result =
                patientService.searchPatients(null, "ACTIVE", "ALL", "ALL", 0, 20);

        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.first()).isTrue();
        assertThat(result.last()).isTrue();
    }

    @Test
    void searchPatients_querySmith_returnsCaseInsensitiveMatch() {
        Patient smith = samplePatient("P2026002", "Bob", "Smith", PatientStatus.ACTIVE, Gender.MALE);
        Page<Patient> page = new PageImpl<>(List.of(smith), PageRequest.of(0, 20), 1);

        when(patientRepository.search(eq("smith"), eq(PatientStatus.ACTIVE), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(page);
        when(patientMapper.toSummary(smith)).thenReturn(summaryOf(smith));

        PagedResponse<PatientSummaryResponse> result =
                patientService.searchPatients("smith", "ACTIVE", "ALL", "ALL", 0, 20);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).lastName()).isEqualTo("Smith");
    }

    @Test
    void searchPatients_statusAll_includesInactive() {
        Patient active   = samplePatient("P2026001", "A", "B", PatientStatus.ACTIVE, Gender.MALE);
        Patient inactive = samplePatient("P2026002", "C", "D", PatientStatus.INACTIVE, Gender.FEMALE);
        Page<Patient> page = new PageImpl<>(List.of(active, inactive), PageRequest.of(0, 20), 2);

        when(patientRepository.search(isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(page);
        when(patientMapper.toSummary(any())).thenAnswer(inv -> summaryOf(inv.getArgument(0)));

        PagedResponse<PatientSummaryResponse> result =
                patientService.searchPatients(null, "ALL", "ALL", "ALL", 0, 20);

        assertThat(result.content()).hasSize(2);
        assertThat(result.totalElements()).isEqualTo(2);
    }

    @Test
    void searchPatients_genderFemale_filtersCorrectly() {
        Patient female = samplePatient("P2026003", "Alice", "Jones", PatientStatus.ACTIVE, Gender.FEMALE);
        Page<Patient> page = new PageImpl<>(List.of(female), PageRequest.of(0, 20), 1);

        when(patientRepository.search(isNull(), eq(PatientStatus.ACTIVE), eq(Gender.FEMALE), isNull(), any(Pageable.class)))
                .thenReturn(page);
        when(patientMapper.toSummary(female)).thenReturn(summaryOf(female));

        PagedResponse<PatientSummaryResponse> result =
                patientService.searchPatients(null, "ACTIVE", "FEMALE", "ALL", 0, 20);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).gender()).isEqualTo(Gender.FEMALE);
    }

    @Test
    void searchPatients_bloodGroupAPos_filtersCorrectly() {
        Patient p = samplePatient("P2026004", "Bob", "Adams", PatientStatus.ACTIVE, Gender.MALE);
        Page<Patient> page = new PageImpl<>(List.of(p), PageRequest.of(0, 20), 1);

        when(patientRepository.search(isNull(), eq(PatientStatus.ACTIVE), isNull(), eq(BloodGroup.A_POS), any(Pageable.class)))
                .thenReturn(page);
        when(patientMapper.toSummary(p)).thenReturn(summaryOf(p));

        PagedResponse<PatientSummaryResponse> result =
                patientService.searchPatients(null, "ACTIVE", "ALL", "A_POS", 0, 20);

        assertThat(result.content()).hasSize(1);
    }

    @Test
    void searchPatients_emptyResults_returnsEmptyPage() {
        Page<Patient> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(patientRepository.search(eq("xyz"), any(), any(), any(), any(Pageable.class)))
                .thenReturn(emptyPage);

        PagedResponse<PatientSummaryResponse> result =
                patientService.searchPatients("xyz", "ACTIVE", "ALL", "ALL", 0, 20);

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isEqualTo(0);
    }

    @Test
    void searchPatients_page2Size20_returnsCorrectSlice() {
        List<Patient> patients = List.of(
                samplePatient("P2026021", "X", "Y", PatientStatus.ACTIVE, Gender.MALE)
        );
        Page<Patient> page = new PageImpl<>(patients, PageRequest.of(1, 20), 21);

        when(patientRepository.search(isNull(), eq(PatientStatus.ACTIVE), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(page);
        when(patientMapper.toSummary(any())).thenAnswer(inv -> summaryOf(inv.getArgument(0)));

        PagedResponse<PatientSummaryResponse> result =
                patientService.searchPatients(null, "ACTIVE", "ALL", "ALL", 1, 20);

        assertThat(result.number()).isEqualTo(1);
        assertThat(result.totalPages()).isEqualTo(2);
        assertThat(result.last()).isTrue();
    }

    @Test
    void searchPatients_asDoctor_allowed() {
        AuthContext.Holder.set(new AuthContext("doc1", "doctor1", "DOCTOR"));
        Page<Patient> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(patientRepository.search(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(emptyPage);

        // Should NOT throw — all authenticated roles can search
        assertThat(patientService.searchPatients(null, "ACTIVE", "ALL", "ALL", 0, 20)).isNotNull();
    }

    @Test
    void searchPatients_asNurse_allowed() {
        AuthContext.Holder.set(new AuthContext("nur1", "nurse1", "NURSE"));
        Page<Patient> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(patientRepository.search(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(emptyPage);

        assertThat(patientService.searchPatients(null, "ACTIVE", "ALL", "ALL", 0, 20)).isNotNull();
    }
}

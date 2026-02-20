package com.ainexus.hospital.patient.unit.service;

import com.ainexus.hospital.patient.audit.AppointmentAuditService;
import com.ainexus.hospital.patient.dto.request.AppointmentStatusChangeRequest;
import com.ainexus.hospital.patient.dto.request.BookAppointmentRequest;
import com.ainexus.hospital.patient.dto.request.UpdateAppointmentRequest;
import com.ainexus.hospital.patient.dto.response.AppointmentResponse;
import com.ainexus.hospital.patient.dto.response.AppointmentStatusChangeResponse;
import com.ainexus.hospital.patient.entity.*;
import com.ainexus.hospital.patient.exception.ConflictException;
import com.ainexus.hospital.patient.exception.ForbiddenException;
import com.ainexus.hospital.patient.exception.ResourceNotFoundException;
import com.ainexus.hospital.patient.exception.VersionConflictException;
import com.ainexus.hospital.patient.mapper.AppointmentMapper;
import com.ainexus.hospital.patient.repository.AppointmentRepository;
import com.ainexus.hospital.patient.repository.HospitalUserRepository;
import com.ainexus.hospital.patient.repository.PatientRepository;
import com.ainexus.hospital.patient.security.AuthContext;
import com.ainexus.hospital.patient.security.RoleGuard;
import com.ainexus.hospital.patient.service.AppointmentIdGeneratorService;
import com.ainexus.hospital.patient.service.AppointmentService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    @Mock private AppointmentRepository appointmentRepository;
    @Mock private PatientRepository patientRepository;
    @Mock private HospitalUserRepository hospitalUserRepository;
    @Mock private AppointmentIdGeneratorService idGeneratorService;
    @Mock private AppointmentAuditService auditService;
    @Mock private AppointmentMapper mapper;
    @Mock private EntityManager entityManager;

    // Real implementations — RoleGuard reads from AuthContext.Holder (ThreadLocal)
    private final RoleGuard roleGuard = new RoleGuard();
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private AppointmentService appointmentService;

    // Fixed test clock values
    private static final LocalDate TEST_DATE = LocalDate.of(2026, 6, 15);
    private static final LocalTime TEST_START = LocalTime.of(9, 0);
    private static final String PATIENT_ID = "P2026001";
    private static final String DOCTOR_ID = "D2026001";
    private static final String APPOINTMENT_ID = "APT20260001";

    @BeforeEach
    void setUpService() {
        appointmentService = new AppointmentService(
                appointmentRepository, patientRepository, hospitalUserRepository,
                idGeneratorService, auditService, mapper, roleGuard, meterRegistry, entityManager
        );
    }

    @AfterEach
    void clearAuth() {
        AuthContext.Holder.clear();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Patient activePatient() {
        return Patient.builder()
                .patientId(PATIENT_ID)
                .firstName("Jane")
                .lastName("Smith")
                .dateOfBirth(LocalDate.of(1985, 6, 15))
                .gender(Gender.FEMALE)
                .bloodGroup(BloodGroup.UNKNOWN)
                .phone("555-123-4567")
                .status(PatientStatus.ACTIVE)
                .createdAt(OffsetDateTime.now())
                .createdBy("admin")
                .updatedAt(OffsetDateTime.now())
                .updatedBy("admin")
                .version(0)
                .build();
    }

    private Patient inactivePatient() {
        return Patient.builder()
                .patientId(PATIENT_ID)
                .firstName("John")
                .lastName("Doe")
                .dateOfBirth(LocalDate.of(1980, 1, 1))
                .gender(Gender.MALE)
                .bloodGroup(BloodGroup.UNKNOWN)
                .phone("555-999-0000")
                .status(PatientStatus.INACTIVE)
                .createdAt(OffsetDateTime.now())
                .createdBy("admin")
                .updatedAt(OffsetDateTime.now())
                .updatedBy("admin")
                .version(0)
                .build();
    }

    private HospitalUser activeDoctor() {
        return HospitalUser.builder()
                .userId(DOCTOR_ID)
                .username("dr.house")
                .passwordHash("$2a$10$irrelevant")
                .role("DOCTOR")
                .status("ACTIVE")
                .version(0)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    private BookAppointmentRequest validBookRequest() {
        return new BookAppointmentRequest(
                PATIENT_ID, DOCTOR_ID, TEST_DATE, TEST_START, 30,
                AppointmentType.GENERAL_CONSULTATION, "Routine check-up", null
        );
    }

    private Appointment scheduledAppointment() {
        return Appointment.builder()
                .appointmentId(APPOINTMENT_ID)
                .patientId(PATIENT_ID)
                .doctorId(DOCTOR_ID)
                .appointmentDate(TEST_DATE)
                .startTime(TEST_START)
                .endTime(TEST_START.plusMinutes(30))
                .durationMinutes(30)
                .type(AppointmentType.GENERAL_CONSULTATION)
                .status(AppointmentStatus.SCHEDULED)
                .reason("Routine check-up")
                .createdBy("receptionist1")
                .updatedBy("receptionist1")
                .version(0)
                .build();
    }

    private Appointment completedAppointment() {
        return Appointment.builder()
                .appointmentId(APPOINTMENT_ID)
                .patientId(PATIENT_ID)
                .doctorId(DOCTOR_ID)
                .appointmentDate(TEST_DATE)
                .startTime(TEST_START)
                .endTime(TEST_START.plusMinutes(30))
                .durationMinutes(30)
                .type(AppointmentType.GENERAL_CONSULTATION)
                .status(AppointmentStatus.COMPLETED)
                .reason("Done")
                .createdBy("receptionist1")
                .updatedBy("receptionist1")
                .version(0)
                .build();
    }

    // ── US1: bookAppointment ──────────────────────────────────────────────────

    @Test
    void bookAppointment_success() {
        AuthContext.Holder.set(new AuthContext("rec1", "receptionist1", "RECEPTIONIST"));

        Patient patient = activePatient();
        HospitalUser doctor = activeDoctor();
        Appointment saved = scheduledAppointment();

        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(patient));
        when(hospitalUserRepository.findById(DOCTOR_ID)).thenReturn(Optional.of(doctor));
        when(appointmentRepository.findOverlappingAppointments(
                eq(DOCTOR_ID), eq(TEST_DATE), eq(TEST_START), eq(TEST_START.plusMinutes(30)), isNull()))
                .thenReturn(Collections.emptyList());
        when(idGeneratorService.generateAppointmentId()).thenReturn(APPOINTMENT_ID);
        when(appointmentRepository.save(any(Appointment.class))).thenReturn(saved);

        // mapper.toResponse is called after save — also stub patientRepo lookup for resolvePatientName
        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(patient));
        when(hospitalUserRepository.findById(DOCTOR_ID)).thenReturn(Optional.of(doctor));

        AppointmentResponse fakeResponse = new AppointmentResponse(
                APPOINTMENT_ID, PATIENT_ID, "Jane Smith", DOCTOR_ID, "dr.house",
                TEST_DATE, TEST_START, TEST_START.plusMinutes(30), 30,
                AppointmentType.GENERAL_CONSULTATION, AppointmentStatus.SCHEDULED,
                "Routine check-up", null, null, 0,
                null, "receptionist1", null, "receptionist1"
        );
        when(mapper.toResponse(any(Appointment.class), anyString(), anyString())).thenReturn(fakeResponse);

        AppointmentResponse result = appointmentService.bookAppointment(validBookRequest());

        assertThat(result.appointmentId()).isEqualTo(APPOINTMENT_ID);
        assertThat(result.status()).isEqualTo(AppointmentStatus.SCHEDULED);

        verify(appointmentRepository).save(any(Appointment.class));
        verify(auditService).writeAuditLog(
                eq(APPOINTMENT_ID), eq("BOOK"), isNull(),
                eq(AppointmentStatus.SCHEDULED), eq("receptionist1"), isNull());
    }

    @Test
    void bookAppointment_forbiddenForNurse() {
        AuthContext.Holder.set(new AuthContext("nur1", "nurse1", "NURSE"));

        assertThatThrownBy(() -> appointmentService.bookAppointment(validBookRequest()))
                .isInstanceOf(ForbiddenException.class);

        verifyNoInteractions(patientRepository, appointmentRepository, idGeneratorService);
    }

    @Test
    void bookAppointment_patientNotFound() {
        AuthContext.Holder.set(new AuthContext("rec1", "receptionist1", "RECEPTIONIST"));

        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appointmentService.bookAppointment(validBookRequest()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(PATIENT_ID);

        verifyNoInteractions(appointmentRepository, idGeneratorService, auditService);
    }

    @Test
    void bookAppointment_patientNotActive() {
        AuthContext.Holder.set(new AuthContext("rec1", "receptionist1", "RECEPTIONIST"));

        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(inactivePatient()));

        assertThatThrownBy(() -> appointmentService.bookAppointment(validBookRequest()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("not ACTIVE");

        verifyNoInteractions(appointmentRepository, idGeneratorService, auditService);
    }

    @Test
    void bookAppointment_conflictDetected() {
        AuthContext.Holder.set(new AuthContext("rec1", "receptionist1", "RECEPTIONIST"));

        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(activePatient()));
        when(hospitalUserRepository.findById(DOCTOR_ID)).thenReturn(Optional.of(activeDoctor()));
        when(appointmentRepository.findOverlappingAppointments(
                eq(DOCTOR_ID), eq(TEST_DATE), eq(TEST_START), eq(TEST_START.plusMinutes(30)), isNull()))
                .thenReturn(List.of(scheduledAppointment()));

        assertThatThrownBy(() -> appointmentService.bookAppointment(validBookRequest()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("conflicting appointment");

        verifyNoInteractions(idGeneratorService, auditService);
    }

    // ── US3: changeStatus ─────────────────────────────────────────────────────

    @Test
    void changeStatus_confirmSuccess() {
        AuthContext.Holder.set(new AuthContext("rec1", "receptionist1", "RECEPTIONIST"));

        when(appointmentRepository.findById(APPOINTMENT_ID))
                .thenReturn(Optional.of(scheduledAppointment()));
        when(appointmentRepository.save(any(Appointment.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        AppointmentStatusChangeRequest request =
                new AppointmentStatusChangeRequest(AppointmentAction.CONFIRM, null);

        AppointmentStatusChangeResponse response =
                appointmentService.changeStatus(APPOINTMENT_ID, request);

        assertThat(response.previousStatus()).isEqualTo(AppointmentStatus.SCHEDULED);
        assertThat(response.newStatus()).isEqualTo(AppointmentStatus.CONFIRMED);

        verify(auditService).writeAuditLog(
                eq(APPOINTMENT_ID), eq("CONFIRM"),
                eq(AppointmentStatus.SCHEDULED), eq(AppointmentStatus.CONFIRMED),
                eq("receptionist1"), isNull());
    }

    @Test
    void changeStatus_forbiddenAction_nurseCannotConfirm() {
        // NURSE is not in ACTION_ROLES for CONFIRM (only RECEPTIONIST, ADMIN)
        // changeStatus throws before findById is called, so no stub needed
        AuthContext.Holder.set(new AuthContext("nur1", "nurse1", "NURSE"));

        AppointmentStatusChangeRequest request =
                new AppointmentStatusChangeRequest(AppointmentAction.CONFIRM, null);

        assertThatThrownBy(() -> appointmentService.changeStatus(APPOINTMENT_ID, request))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("NURSE");

        verifyNoInteractions(auditService);
    }

    @Test
    void changeStatus_invalidTransition_confirmOnCompleted() {
        AuthContext.Holder.set(new AuthContext("rec1", "receptionist1", "RECEPTIONIST"));

        when(appointmentRepository.findById(APPOINTMENT_ID))
                .thenReturn(Optional.of(completedAppointment()));

        AppointmentStatusChangeRequest request =
                new AppointmentStatusChangeRequest(AppointmentAction.CONFIRM, null);

        assertThatThrownBy(() -> appointmentService.changeStatus(APPOINTMENT_ID, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("COMPLETED");

        verifyNoInteractions(auditService);
    }

    @Test
    void changeStatus_cancelRequiresReason() {
        // CANCEL reason check happens before findAppointmentOrThrow; no stub needed
        AuthContext.Holder.set(new AuthContext("rec1", "receptionist1", "RECEPTIONIST"));

        AppointmentStatusChangeRequest request =
                new AppointmentStatusChangeRequest(AppointmentAction.CANCEL, null);

        assertThatThrownBy(() -> appointmentService.changeStatus(APPOINTMENT_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cancel reason is required");

        verifyNoInteractions(auditService);
    }

    @Test
    void changeStatus_adminEscapeHatch_cancelFromCompleted() {
        // ADMIN can cancel from any status, bypassing normal transition rules
        AuthContext.Holder.set(new AuthContext("adm1", "admin1", "ADMIN"));

        when(appointmentRepository.findById(APPOINTMENT_ID))
                .thenReturn(Optional.of(completedAppointment()));
        when(appointmentRepository.save(any(Appointment.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        AppointmentStatusChangeRequest request =
                new AppointmentStatusChangeRequest(AppointmentAction.CANCEL, "admin override");

        AppointmentStatusChangeResponse response =
                appointmentService.changeStatus(APPOINTMENT_ID, request);

        assertThat(response.previousStatus()).isEqualTo(AppointmentStatus.COMPLETED);
        assertThat(response.newStatus()).isEqualTo(AppointmentStatus.CANCELLED);

        verify(auditService).writeAuditLog(
                eq(APPOINTMENT_ID), eq("CANCEL"),
                eq(AppointmentStatus.COMPLETED), eq(AppointmentStatus.CANCELLED),
                eq("admin1"), eq("admin override"));
    }

    // ── US4: updateAppointment ────────────────────────────────────────────────

    @Test
    void updateAppointment_versionMismatch() {
        AuthContext.Holder.set(new AuthContext("rec1", "receptionist1", "RECEPTIONIST"));

        // appointment.version = 1, but client sends version = 0 → mismatch
        Appointment staleAppointment = Appointment.builder()
                .appointmentId(APPOINTMENT_ID)
                .patientId(PATIENT_ID)
                .doctorId(DOCTOR_ID)
                .appointmentDate(TEST_DATE)
                .startTime(TEST_START)
                .endTime(TEST_START.plusMinutes(30))
                .durationMinutes(30)
                .type(AppointmentType.GENERAL_CONSULTATION)
                .status(AppointmentStatus.SCHEDULED)
                .reason("Routine")
                .createdBy("receptionist1")
                .updatedBy("receptionist1")
                .version(1)   // current persisted version
                .build();

        when(appointmentRepository.findById(APPOINTMENT_ID))
                .thenReturn(Optional.of(staleAppointment));

        UpdateAppointmentRequest request = new UpdateAppointmentRequest(
                null, null, null, null, "Updated reason", null
        );

        // Client thinks version is 0, server has version 1 → VersionConflictException
        assertThatThrownBy(() -> appointmentService.updateAppointment(APPOINTMENT_ID, 0, request))
                .isInstanceOf(VersionConflictException.class)
                .hasMessageContaining("version mismatch");

        verifyNoInteractions(auditService);
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void updateAppointment_wrongStatus_completedCannotBeUpdated() {
        AuthContext.Holder.set(new AuthContext("rec1", "receptionist1", "RECEPTIONIST"));

        Appointment completed = completedAppointment();
        // version matches: appointment.version=0, client sends 0
        when(appointmentRepository.findById(APPOINTMENT_ID))
                .thenReturn(Optional.of(completed));

        UpdateAppointmentRequest request = new UpdateAppointmentRequest(
                null, null, null, null, "Should fail", null
        );

        assertThatThrownBy(() -> appointmentService.updateAppointment(APPOINTMENT_ID, 0, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("COMPLETED");

        verifyNoInteractions(auditService);
        verify(appointmentRepository, never()).save(any());
    }
}

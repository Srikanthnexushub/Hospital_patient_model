package com.ainexus.hospital.patient.unit.service;

import com.ainexus.hospital.patient.dto.response.*;
import com.ainexus.hospital.patient.entity.Appointment;
import com.ainexus.hospital.patient.entity.AppointmentStatus;
import com.ainexus.hospital.patient.exception.ForbiddenException;
import com.ainexus.hospital.patient.exception.ResourceNotFoundException;
import com.ainexus.hospital.patient.repository.AppointmentRepository;
import com.ainexus.hospital.patient.repository.PatientRepository;
import com.ainexus.hospital.patient.security.AuthContext;
import com.ainexus.hospital.patient.security.RoleGuard;
import com.ainexus.hospital.patient.service.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MedicalSummaryServiceTest {

    @Mock private PatientRepository patientRepository;
    @Mock private AppointmentRepository appointmentRepository;
    @Mock private ProblemService problemService;
    @Mock private MedicationService medicationService;
    @Mock private AllergyService allergyService;
    @Mock private VitalsService vitalsService;

    private MedicalSummaryService medicalSummaryService;

    private static final String PATIENT_ID = "P2025001";

    @BeforeEach
    void setUp() {
        medicalSummaryService = new MedicalSummaryService(
                patientRepository, appointmentRepository,
                problemService, medicationService, allergyService, vitalsService,
                new RoleGuard());
    }

    @AfterEach
    void clearContext() {
        AuthContext.Holder.clear();
    }

    private void setAuth(String role) {
        AuthContext.Holder.set(new AuthContext("uid001", role.toLowerCase() + "1", role));
    }

    @Test
    void getMedicalSummary_doctorRole_returnsSummary() {
        setAuth("DOCTOR");
        when(patientRepository.existsById(PATIENT_ID)).thenReturn(true);

        Appointment lastAppt = new Appointment();
        lastAppt.setAppointmentDate(LocalDate.of(2026, 1, 15));
        when(appointmentRepository.findFirstByPatientIdAndStatusOrderByAppointmentDateDesc(
                PATIENT_ID, AppointmentStatus.COMPLETED))
                .thenReturn(Optional.of(lastAppt));
        when(appointmentRepository.countByPatientId(PATIENT_ID)).thenReturn(5L);

        when(problemService.getActiveProblems(PATIENT_ID)).thenReturn(Collections.emptyList());
        when(medicationService.getActiveMedications(PATIENT_ID)).thenReturn(Collections.emptyList());
        when(allergyService.getActiveAllergies(PATIENT_ID)).thenReturn(Collections.emptyList());
        when(vitalsService.getTop5VitalsByPatient(PATIENT_ID)).thenReturn(Collections.emptyList());

        MedicalSummaryResponse summary = medicalSummaryService.getMedicalSummary(PATIENT_ID);

        assertThat(summary.lastVisitDate()).isEqualTo(LocalDate.of(2026, 1, 15));
        assertThat(summary.totalVisits()).isEqualTo(5L);
        assertThat(summary.activeProblems()).isEmpty();
        assertThat(summary.activeMedications()).isEmpty();
        assertThat(summary.allergies()).isEmpty();
        assertThat(summary.recentVitals()).isEmpty();
    }

    @Test
    void getMedicalSummary_noCompletedAppointments_lastVisitDateIsNull() {
        setAuth("ADMIN");
        when(patientRepository.existsById(PATIENT_ID)).thenReturn(true);
        when(appointmentRepository.findFirstByPatientIdAndStatusOrderByAppointmentDateDesc(
                PATIENT_ID, AppointmentStatus.COMPLETED))
                .thenReturn(Optional.empty());
        when(appointmentRepository.countByPatientId(PATIENT_ID)).thenReturn(0L);
        when(problemService.getActiveProblems(PATIENT_ID)).thenReturn(Collections.emptyList());
        when(medicationService.getActiveMedications(PATIENT_ID)).thenReturn(Collections.emptyList());
        when(allergyService.getActiveAllergies(PATIENT_ID)).thenReturn(Collections.emptyList());
        when(vitalsService.getTop5VitalsByPatient(PATIENT_ID)).thenReturn(Collections.emptyList());

        MedicalSummaryResponse summary = medicalSummaryService.getMedicalSummary(PATIENT_ID);

        assertThat(summary.lastVisitDate()).isNull();
        assertThat(summary.totalVisits()).isZero();
    }

    @Test
    void getMedicalSummary_nurseRole_throwsForbidden() {
        setAuth("NURSE");

        assertThatThrownBy(() -> medicalSummaryService.getMedicalSummary(PATIENT_ID))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void getMedicalSummary_patientNotFound_throws404() {
        setAuth("DOCTOR");
        when(patientRepository.existsById(PATIENT_ID)).thenReturn(false);

        assertThatThrownBy(() -> medicalSummaryService.getMedicalSummary(PATIENT_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}

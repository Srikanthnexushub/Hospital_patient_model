package com.ainexus.hospital.patient.unit.service;

import com.ainexus.hospital.patient.audit.EmrAuditService;
import com.ainexus.hospital.patient.dto.request.RecordVitalsRequest;
import com.ainexus.hospital.patient.dto.response.VitalsResponse;
import com.ainexus.hospital.patient.entity.Appointment;
import com.ainexus.hospital.patient.entity.PatientVitals;
import com.ainexus.hospital.patient.exception.ForbiddenException;
import com.ainexus.hospital.patient.exception.ResourceNotFoundException;
import com.ainexus.hospital.patient.mapper.VitalsMapper;
import com.ainexus.hospital.patient.repository.AppointmentRepository;
import com.ainexus.hospital.patient.repository.PatientRepository;
import com.ainexus.hospital.patient.repository.VitalsRepository;
import com.ainexus.hospital.patient.security.AuthContext;
import com.ainexus.hospital.patient.security.RoleGuard;
import com.ainexus.hospital.patient.service.VitalsService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VitalsServiceTest {

    @Mock private VitalsRepository vitalsRepository;
    @Mock private AppointmentRepository appointmentRepository;
    @Mock private PatientRepository patientRepository;
    @Mock private EmrAuditService emrAuditService;

    private VitalsService vitalsService;

    private static final String APPT_ID    = "APT2025001";
    private static final String PATIENT_ID = "P2025001";

    @BeforeEach
    void setUp() {
        vitalsService = new VitalsService(
                vitalsRepository, appointmentRepository, patientRepository,
                new VitalsMapper(), emrAuditService,
                new RoleGuard(), new SimpleMeterRegistry());
    }

    @AfterEach
    void clearContext() {
        AuthContext.Holder.clear();
    }

    private void setAuth(String role) {
        AuthContext.Holder.set(new AuthContext("uid001", role.toLowerCase() + "1", role));
    }

    private Appointment stubAppointment() {
        Appointment appt = new Appointment();
        appt.setAppointmentId(APPT_ID);
        appt.setPatientId(PATIENT_ID);
        return appt;
    }

    private PatientVitals stubVitals() {
        PatientVitals v = new PatientVitals();
        v.setId(1L);
        v.setAppointmentId(APPT_ID);
        v.setPatientId(PATIENT_ID);
        v.setHeartRate(72);
        v.setRecordedBy("nurse1");
        v.setRecordedAt(OffsetDateTime.now());
        return v;
    }

    // ── recordVitals ──────────────────────────────────────────────────────────

    @Test
    void recordVitals_newRecord_createsAndAudits() {
        setAuth("NURSE");
        when(appointmentRepository.findById(APPT_ID)).thenReturn(Optional.of(stubAppointment()));
        when(vitalsRepository.findByAppointmentId(APPT_ID)).thenReturn(Optional.empty());
        when(vitalsRepository.save(any())).thenAnswer(inv -> {
            PatientVitals v = inv.getArgument(0);
            v.setId(1L);
            return v;
        });

        RecordVitalsRequest request = new RecordVitalsRequest(
                null, null, 72, null, null, null, null, null);

        VitalsResponse response = vitalsService.recordVitals(APPT_ID, request);

        assertThat(response.heartRate()).isEqualTo(72);
        verify(emrAuditService).writeAuditLog(eq("VITAL"), any(), eq(PATIENT_ID),
                eq("CREATE"), anyString(), isNull());
    }

    @Test
    void recordVitals_existingRecord_updatesAndAudits() {
        setAuth("DOCTOR");
        when(appointmentRepository.findById(APPT_ID)).thenReturn(Optional.of(stubAppointment()));
        when(vitalsRepository.findByAppointmentId(APPT_ID)).thenReturn(Optional.of(stubVitals()));
        when(vitalsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RecordVitalsRequest request = new RecordVitalsRequest(
                null, null, 80, null, null, null, null, null);

        VitalsResponse response = vitalsService.recordVitals(APPT_ID, request);

        assertThat(response.heartRate()).isEqualTo(80);
        verify(emrAuditService).writeAuditLog(eq("VITAL"), any(), eq(PATIENT_ID),
                eq("UPDATE"), anyString(), isNull());
    }

    @Test
    void recordVitals_bpDiastolicExceedsSystolic_throws400() {
        setAuth("NURSE");
        when(appointmentRepository.findById(APPT_ID)).thenReturn(Optional.of(stubAppointment()));

        RecordVitalsRequest request = new RecordVitalsRequest(
                100, 120, null, null, null, null, null, null);

        assertThatThrownBy(() -> vitalsService.recordVitals(APPT_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("diastolic");
    }

    @Test
    void recordVitals_appointmentNotFound_throws404() {
        setAuth("NURSE");
        when(appointmentRepository.findById(APPT_ID)).thenReturn(Optional.empty());

        RecordVitalsRequest request = new RecordVitalsRequest(
                null, null, 72, null, null, null, null, null);

        assertThatThrownBy(() -> vitalsService.recordVitals(APPT_ID, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void recordVitals_receptionistRole_throwsForbidden() {
        setAuth("RECEPTIONIST");

        RecordVitalsRequest request = new RecordVitalsRequest(
                null, null, 72, null, null, null, null, null);

        assertThatThrownBy(() -> vitalsService.recordVitals(APPT_ID, request))
                .isInstanceOf(ForbiddenException.class);
    }

    // ── getVitalsByAppointment ────────────────────────────────────────────────

    @Test
    void getVitalsByAppointment_found_returnsResponse() {
        setAuth("DOCTOR");
        when(appointmentRepository.existsById(APPT_ID)).thenReturn(true);
        when(vitalsRepository.findByAppointmentId(APPT_ID)).thenReturn(Optional.of(stubVitals()));

        VitalsResponse response = vitalsService.getVitalsByAppointment(APPT_ID);

        assertThat(response.appointmentId()).isEqualTo(APPT_ID);
        assertThat(response.heartRate()).isEqualTo(72);
    }

    @Test
    void getVitalsByAppointment_noVitals_throws404() {
        setAuth("NURSE");
        when(appointmentRepository.existsById(APPT_ID)).thenReturn(true);
        when(vitalsRepository.findByAppointmentId(APPT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> vitalsService.getVitalsByAppointment(APPT_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}

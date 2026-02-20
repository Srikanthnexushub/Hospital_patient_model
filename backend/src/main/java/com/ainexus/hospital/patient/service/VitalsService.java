package com.ainexus.hospital.patient.service;

import com.ainexus.hospital.patient.audit.EmrAuditService;
import com.ainexus.hospital.patient.dto.request.RecordVitalsRequest;
import com.ainexus.hospital.patient.dto.response.VitalsResponse;
import com.ainexus.hospital.patient.entity.Appointment;
import com.ainexus.hospital.patient.entity.PatientVitals;
import com.ainexus.hospital.patient.exception.ResourceNotFoundException;
import com.ainexus.hospital.patient.mapper.VitalsMapper;
import com.ainexus.hospital.patient.repository.AppointmentRepository;
import com.ainexus.hospital.patient.repository.PatientRepository;
import com.ainexus.hospital.patient.repository.VitalsRepository;
import com.ainexus.hospital.patient.security.AuthContext;
import com.ainexus.hospital.patient.security.RoleGuard;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class VitalsService {

    private final VitalsRepository vitalsRepository;
    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final VitalsMapper vitalsMapper;
    private final EmrAuditService emrAuditService;
    private final RoleGuard roleGuard;
    private final MeterRegistry meterRegistry;

    public VitalsService(VitalsRepository vitalsRepository,
                         AppointmentRepository appointmentRepository,
                         PatientRepository patientRepository,
                         VitalsMapper vitalsMapper,
                         EmrAuditService emrAuditService,
                         RoleGuard roleGuard,
                         MeterRegistry meterRegistry) {
        this.vitalsRepository = vitalsRepository;
        this.appointmentRepository = appointmentRepository;
        this.patientRepository = patientRepository;
        this.vitalsMapper = vitalsMapper;
        this.emrAuditService = emrAuditService;
        this.roleGuard = roleGuard;
        this.meterRegistry = meterRegistry;
    }

    // ── US1: Record / Replace Vitals (upsert) ─────────────────────────────────

    @Transactional
    public VitalsResponse recordVitals(String appointmentId, RecordVitalsRequest request) {
        roleGuard.requireRoles("NURSE", "DOCTOR", "ADMIN");
        AuthContext ctx = AuthContext.Holder.get();

        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found: " + appointmentId));

        String patientId = appointment.getPatientId();

        // BP cross-field validation
        if (request.bloodPressureSystolic() != null && request.bloodPressureDiastolic() != null
                && request.bloodPressureDiastolic() > request.bloodPressureSystolic()) {
            throw new IllegalArgumentException(
                    "Blood pressure diastolic must not exceed systolic.");
        }

        Optional<PatientVitals> existing = vitalsRepository.findByAppointmentId(appointmentId);
        PatientVitals vitals = existing.orElseGet(PatientVitals::new);
        String action = existing.isPresent() ? "UPDATE" : "CREATE";

        vitals.setAppointmentId(appointmentId);
        vitals.setPatientId(patientId);
        vitals.setBloodPressureSystolic(request.bloodPressureSystolic());
        vitals.setBloodPressureDiastolic(request.bloodPressureDiastolic());
        vitals.setHeartRate(request.heartRate());
        vitals.setTemperature(request.temperature());
        vitals.setWeight(request.weight());
        vitals.setHeight(request.height());
        vitals.setOxygenSaturation(request.oxygenSaturation());
        vitals.setRespiratoryRate(request.respiratoryRate());
        vitals.setRecordedBy(ctx.getUsername());
        vitals.setRecordedAt(OffsetDateTime.now());

        vitalsRepository.save(vitals);

        emrAuditService.writeAuditLog(
                "VITAL", String.valueOf(vitals.getId()), patientId,
                action, ctx.getUsername(), null);

        meterRegistry.counter("emr.vitals.upserted").increment();

        return vitalsMapper.toResponse(vitals);
    }

    // ── US1: Get Vitals for Appointment ───────────────────────────────────────

    @Transactional(readOnly = true)
    public VitalsResponse getVitalsByAppointment(String appointmentId) {
        roleGuard.requireRoles("NURSE", "DOCTOR", "ADMIN");

        if (!appointmentRepository.existsById(appointmentId)) {
            throw new ResourceNotFoundException("Appointment not found: " + appointmentId);
        }

        PatientVitals vitals = vitalsRepository.findByAppointmentId(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No vitals recorded for appointment: " + appointmentId));

        return vitalsMapper.toResponse(vitals);
    }

    // ── US1: Vitals History for Patient ───────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<VitalsResponse> getVitalsByPatient(String patientId, int page, int size) {
        roleGuard.requireRoles("NURSE", "DOCTOR", "ADMIN");

        if (!patientRepository.existsById(patientId)) {
            throw new ResourceNotFoundException("Patient not found: " + patientId);
        }

        return vitalsRepository
                .findByPatientIdOrderByRecordedAtDesc(patientId, PageRequest.of(page, size))
                .map(vitalsMapper::toResponse);
    }

    /** Used by MedicalSummaryService — no role check (caller already checked). */
    @Transactional(readOnly = true)
    public List<VitalsResponse> getTop5VitalsByPatient(String patientId) {
        return vitalsRepository.findTop5ByPatientIdOrderByRecordedAtDesc(patientId)
                .stream().map(vitalsMapper::toResponse).toList();
    }
}

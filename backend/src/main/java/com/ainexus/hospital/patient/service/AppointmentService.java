package com.ainexus.hospital.patient.service;

import com.ainexus.hospital.patient.audit.AppointmentAuditService;
import com.ainexus.hospital.patient.dto.request.BookAppointmentRequest;
import com.ainexus.hospital.patient.dto.request.UpdateAppointmentRequest;
import com.ainexus.hospital.patient.dto.request.AppointmentStatusChangeRequest;
import com.ainexus.hospital.patient.dto.response.*;
import com.ainexus.hospital.patient.entity.*;
import com.ainexus.hospital.patient.exception.AppointmentNotFoundException;
import com.ainexus.hospital.patient.exception.ConflictException;
import com.ainexus.hospital.patient.exception.ForbiddenException;
import com.ainexus.hospital.patient.exception.ResourceNotFoundException;
import com.ainexus.hospital.patient.exception.VersionConflictException;
import com.ainexus.hospital.patient.mapper.AppointmentMapper;
import com.ainexus.hospital.patient.repository.AppointmentRepository;
import com.ainexus.hospital.patient.repository.AppointmentSpecifications;
import com.ainexus.hospital.patient.repository.HospitalUserRepository;
import com.ainexus.hospital.patient.repository.PatientRepository;
import com.ainexus.hospital.patient.security.AuthContext;
import com.ainexus.hospital.patient.security.RoleGuard;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AppointmentService {

    // ── Valid transitions: action → allowed current statuses ──────────────────
    private static final Map<AppointmentAction, Set<AppointmentStatus>> VALID_TRANSITIONS = Map.of(
            AppointmentAction.CONFIRM,   Set.of(AppointmentStatus.SCHEDULED),
            AppointmentAction.CHECK_IN,  Set.of(AppointmentStatus.CONFIRMED),
            AppointmentAction.START,     Set.of(AppointmentStatus.CHECKED_IN),
            AppointmentAction.COMPLETE,  Set.of(AppointmentStatus.IN_PROGRESS),
            AppointmentAction.CANCEL,    Set.of(AppointmentStatus.SCHEDULED, AppointmentStatus.CONFIRMED,
                                                 AppointmentStatus.CHECKED_IN, AppointmentStatus.IN_PROGRESS),
            AppointmentAction.NO_SHOW,   Set.of(AppointmentStatus.CONFIRMED)
    );

    // ── Roles allowed per action ───────────────────────────────────────────────
    private static final Map<AppointmentAction, Set<String>> ACTION_ROLES = Map.of(
            AppointmentAction.CONFIRM,   Set.of("RECEPTIONIST", "ADMIN"),
            AppointmentAction.CHECK_IN,  Set.of("RECEPTIONIST", "NURSE", "ADMIN"),
            AppointmentAction.START,     Set.of("DOCTOR", "ADMIN"),
            AppointmentAction.COMPLETE,  Set.of("DOCTOR", "ADMIN"),
            AppointmentAction.CANCEL,    Set.of("RECEPTIONIST", "ADMIN", "DOCTOR"),
            AppointmentAction.NO_SHOW,   Set.of("RECEPTIONIST", "ADMIN")
    );

    private static final Map<AppointmentAction, AppointmentStatus> ACTION_TO_STATUS = Map.of(
            AppointmentAction.CONFIRM,  AppointmentStatus.CONFIRMED,
            AppointmentAction.CHECK_IN, AppointmentStatus.CHECKED_IN,
            AppointmentAction.START,    AppointmentStatus.IN_PROGRESS,
            AppointmentAction.COMPLETE, AppointmentStatus.COMPLETED,
            AppointmentAction.CANCEL,   AppointmentStatus.CANCELLED,
            AppointmentAction.NO_SHOW,  AppointmentStatus.NO_SHOW
    );

    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final HospitalUserRepository hospitalUserRepository;
    private final AppointmentIdGeneratorService idGeneratorService;
    private final AppointmentAuditService auditService;
    private final AppointmentMapper mapper;
    private final RoleGuard roleGuard;
    private final MeterRegistry meterRegistry;
    private final EntityManager entityManager;

    public AppointmentService(AppointmentRepository appointmentRepository,
                              PatientRepository patientRepository,
                              HospitalUserRepository hospitalUserRepository,
                              AppointmentIdGeneratorService idGeneratorService,
                              AppointmentAuditService auditService,
                              AppointmentMapper mapper,
                              RoleGuard roleGuard,
                              MeterRegistry meterRegistry,
                              EntityManager entityManager) {
        this.appointmentRepository = appointmentRepository;
        this.patientRepository = patientRepository;
        this.hospitalUserRepository = hospitalUserRepository;
        this.idGeneratorService = idGeneratorService;
        this.auditService = auditService;
        this.mapper = mapper;
        this.roleGuard = roleGuard;
        this.meterRegistry = meterRegistry;
        this.entityManager = entityManager;
    }

    // ── US1: Book Appointment ─────────────────────────────────────────────────

    @Transactional
    public AppointmentResponse bookAppointment(BookAppointmentRequest request) {
        roleGuard.requireRoles("RECEPTIONIST", "ADMIN");

        // Validate patient exists and is ACTIVE
        Patient patient = patientRepository.findById(request.patientId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found: " + request.patientId()));
        if (!"ACTIVE".equals(patient.getStatus().name())) {
            throw new ConflictException("Patient is not ACTIVE and cannot book appointments.");
        }

        // Validate doctor exists, has role=DOCTOR, and is ACTIVE
        HospitalUser doctor = hospitalUserRepository.findById(request.doctorId())
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found: " + request.doctorId()));
        if (!"DOCTOR".equals(doctor.getRole())) {
            throw new ResourceNotFoundException("Doctor not found: " + request.doctorId());
        }
        if (!"ACTIVE".equals(doctor.getStatus())) {
            throw new ConflictException("Doctor is not ACTIVE and cannot accept appointments.");
        }

        // Validate durationMinutes
        validateDurationMinutes(request.durationMinutes());

        LocalTime endTime = request.startTime().plusMinutes(request.durationMinutes());

        // Conflict detection (SELECT FOR UPDATE)
        List<Appointment> conflicts = appointmentRepository.findOverlappingAppointments(
                request.doctorId(), request.appointmentDate(),
                request.startTime(), endTime, null);
        if (!conflicts.isEmpty()) {
            throw new ConflictException("Doctor has a conflicting appointment in this time slot.");
        }

        String appointmentId = idGeneratorService.generateAppointmentId();
        AuthContext ctx = AuthContext.Holder.get();

        Appointment appointment = Appointment.builder()
                .appointmentId(appointmentId)
                .patientId(request.patientId())
                .doctorId(request.doctorId())
                .appointmentDate(request.appointmentDate())
                .startTime(request.startTime())
                .endTime(endTime)
                .durationMinutes(request.durationMinutes())
                .type(request.type())
                .status(AppointmentStatus.SCHEDULED)
                .reason(request.reason())
                .notes(request.notes())
                .createdBy(ctx.getUsername())
                .updatedBy(ctx.getUsername())
                .build();

        appointmentRepository.save(appointment);

        auditService.writeAuditLog(appointmentId, "BOOK", null,
                AppointmentStatus.SCHEDULED, ctx.getUsername(), null);

        meterRegistry.counter("appointments.booked").increment();

        String patientName = patient.getFirstName() + " " + patient.getLastName();
        return mapper.toResponse(appointment, patientName, doctor.getUsername());
    }

    // ── US2: List/Search Appointments ────────────────────────────────────────

    @Transactional(readOnly = true)
    public PagedResponse<AppointmentSummaryResponse> listAppointments(
            String doctorId, String patientId, LocalDate date,
            LocalDate dateFrom, LocalDate dateTo,
            AppointmentStatus status, AppointmentType type,
            int page, int size) {
        roleGuard.requireAuthenticated();
        size = Math.min(size, 100);

        AuthContext ctx = AuthContext.Holder.get();
        String effectiveDoctorId = doctorId;
        if ("DOCTOR".equals(ctx.getRole())) {
            effectiveDoctorId = ctx.getUserId();
        }

        Pageable pageable = PageRequest.of(page, size,
                Sort.by("appointmentDate").ascending().and(Sort.by("startTime").ascending()));
        Page<Appointment> resultPage = appointmentRepository.findAll(
                AppointmentSpecifications.search(effectiveDoctorId, patientId, date, dateFrom, dateTo, status, type),
                pageable);

        List<AppointmentSummaryResponse> content = resultPage.getContent().stream()
                .map(a -> mapper.toSummary(a, resolvePatientName(a.getPatientId()), resolveDoctorName(a.getDoctorId())))
                .toList();

        return new PagedResponse<>(content, resultPage.getNumber(), resultPage.getSize(),
                resultPage.getTotalElements(), resultPage.getTotalPages(),
                resultPage.isFirst(), resultPage.isLast());
    }

    @Transactional(readOnly = true)
    public PagedResponse<AppointmentSummaryResponse> getTodayAppointments(int page, int size) {
        roleGuard.requireAuthenticated();
        size = Math.min(size, 100);

        AuthContext ctx = AuthContext.Holder.get();
        String effectiveDoctorId = "DOCTOR".equals(ctx.getRole()) ? ctx.getUserId() : null;

        Page<Appointment> resultPage = appointmentRepository.findTodayAppointments(
                LocalDate.now(), effectiveDoctorId, PageRequest.of(page, size));

        List<AppointmentSummaryResponse> content = resultPage.getContent().stream()
                .map(a -> mapper.toSummary(a, resolvePatientName(a.getPatientId()), resolveDoctorName(a.getDoctorId())))
                .toList();

        return new PagedResponse<>(content, resultPage.getNumber(), resultPage.getSize(),
                resultPage.getTotalElements(), resultPage.getTotalPages(),
                resultPage.isFirst(), resultPage.isLast());
    }

    @Transactional(readOnly = true)
    public AppointmentResponse getAppointment(String appointmentId) {
        roleGuard.requireAuthenticated();
        Appointment a = findAppointmentOrThrow(appointmentId);
        return mapper.toResponse(a, resolvePatientName(a.getPatientId()), resolveDoctorName(a.getDoctorId()));
    }

    // ── US3: Status Lifecycle ─────────────────────────────────────────────────

    @Transactional
    public AppointmentStatusChangeResponse changeStatus(String appointmentId,
                                                        AppointmentStatusChangeRequest request) {
        roleGuard.requireAuthenticated();

        AuthContext ctx = AuthContext.Holder.get();
        AppointmentAction action = request.action();

        // Role check for this specific action
        Set<String> allowedRoles = ACTION_ROLES.get(action);
        if (!allowedRoles.contains(ctx.getRole())) {
            throw new ForbiddenException("Role " + ctx.getRole() + " cannot perform action " + action);
        }

        // CANCEL requires reason
        if (AppointmentAction.CANCEL.equals(action) &&
                (request.reason() == null || request.reason().isBlank())) {
            throw new IllegalArgumentException("cancel reason is required");
        }

        Appointment appointment = findAppointmentOrThrow(appointmentId);
        AppointmentStatus currentStatus = appointment.getStatus();

        // ADMIN escape hatch: can cancel from any status
        boolean isAdminCancel = "ADMIN".equals(ctx.getRole()) && AppointmentAction.CANCEL.equals(action);

        if (!isAdminCancel) {
            Set<AppointmentStatus> validFromStatuses = VALID_TRANSITIONS.get(action);
            if (!validFromStatuses.contains(currentStatus)) {
                throw new ConflictException("Cannot perform action " + action +
                        " on appointment with status " + currentStatus);
            }
        }

        AppointmentStatus newStatus = ACTION_TO_STATUS.get(action);
        appointment.setStatus(newStatus);
        appointment.setUpdatedBy(ctx.getUsername());
        if (AppointmentAction.CANCEL.equals(action)) {
            appointment.setCancelReason(request.reason());
        }

        appointmentRepository.save(appointment);

        auditService.writeAuditLog(appointmentId, action.name(), currentStatus, newStatus,
                ctx.getUsername(), request.reason());

        return new AppointmentStatusChangeResponse(
                appointmentId, currentStatus, newStatus,
                "Appointment status updated to " + newStatus);
    }

    // ── US4: Update Appointment Details ───────────────────────────────────────

    @Transactional
    public AppointmentResponse updateAppointment(String appointmentId,
                                                  Integer version,
                                                  UpdateAppointmentRequest request) {
        roleGuard.requireRoles("RECEPTIONIST", "ADMIN");

        Appointment appointment = findAppointmentOrThrow(appointmentId);

        // Version check (optimistic locking via If-Match header)
        if (!appointment.getVersion().equals(version)) {
            throw new VersionConflictException("Appointment version mismatch. Expected " +
                    appointment.getVersion() + " but got " + version + ".");
        }

        // Only SCHEDULED or CONFIRMED can be updated
        if (appointment.getStatus() != AppointmentStatus.SCHEDULED &&
                appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new ConflictException("Cannot update appointment with status " + appointment.getStatus());
        }

        AuthContext ctx = AuthContext.Holder.get();

        // Apply only non-null fields
        LocalDate newDate = request.appointmentDate() != null ? request.appointmentDate() : appointment.getAppointmentDate();
        LocalTime newStart = request.startTime() != null ? request.startTime() : appointment.getStartTime();
        Integer newDuration = request.durationMinutes() != null ? request.durationMinutes() : appointment.getDurationMinutes();
        if (request.durationMinutes() != null) {
            validateDurationMinutes(request.durationMinutes());
        }
        LocalTime newEnd = newStart.plusMinutes(newDuration);

        // Re-run conflict detection if time/date changed
        boolean timeChanged = !newDate.equals(appointment.getAppointmentDate()) ||
                !newStart.equals(appointment.getStartTime()) ||
                !newDuration.equals(appointment.getDurationMinutes());
        if (timeChanged) {
            List<Appointment> conflicts = appointmentRepository.findOverlappingAppointments(
                    appointment.getDoctorId(), newDate, newStart, newEnd, appointmentId);
            if (!conflicts.isEmpty()) {
                throw new ConflictException("Doctor has a conflicting appointment in this time slot.");
            }
        }

        appointment.setAppointmentDate(newDate);
        appointment.setStartTime(newStart);
        appointment.setEndTime(newEnd);
        appointment.setDurationMinutes(newDuration);
        if (request.type() != null) appointment.setType(request.type());
        if (request.reason() != null) appointment.setReason(request.reason());
        if (request.notes() != null) appointment.setNotes(request.notes());
        appointment.setUpdatedBy(ctx.getUsername());

        Appointment saved = appointmentRepository.saveAndFlush(appointment);

        auditService.writeAuditLog(appointmentId, "UPDATE", saved.getStatus(),
                saved.getStatus(), ctx.getUsername(), "Appointment details updated");

        return mapper.toResponse(saved,
                resolvePatientName(saved.getPatientId()),
                resolveDoctorName(saved.getDoctorId()));
    }

    // ── US7: Patient Appointment History ─────────────────────────────────────

    @Transactional(readOnly = true)
    public PagedResponse<AppointmentSummaryResponse> getPatientAppointmentHistory(
            String patientId, int page, int size) {
        roleGuard.requireAuthenticated();
        size = Math.min(size, 100);

        // Validate patient exists
        patientRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found: " + patientId));

        AuthContext ctx = AuthContext.Holder.get();
        String effectiveDoctorId = "DOCTOR".equals(ctx.getRole()) ? ctx.getUserId() : null;

        Page<Appointment> resultPage = appointmentRepository.findPatientAppointmentHistory(
                patientId, effectiveDoctorId, PageRequest.of(page, size));

        List<AppointmentSummaryResponse> content = resultPage.getContent().stream()
                .map(a -> mapper.toSummary(a, resolvePatientName(a.getPatientId()), resolveDoctorName(a.getDoctorId())))
                .toList();

        return new PagedResponse<>(content, resultPage.getNumber(), resultPage.getSize(),
                resultPage.getTotalElements(), resultPage.getTotalPages(),
                resultPage.isFirst(), resultPage.isLast());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Appointment findAppointmentOrThrow(String appointmentId) {
        return appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new AppointmentNotFoundException(appointmentId));
    }

    private String resolvePatientName(String patientId) {
        return patientRepository.findById(patientId)
                .map(p -> p.getFirstName() + " " + p.getLastName())
                .orElse("Unknown");
    }

    private String resolveDoctorName(String doctorId) {
        return hospitalUserRepository.findById(doctorId)
                .map(HospitalUser::getUsername)
                .orElse("Unknown");
    }

    private void validateDurationMinutes(int duration) {
        if (duration != 15 && duration != 30 && duration != 45 &&
                duration != 60 && duration != 90 && duration != 120) {
            throw new IllegalArgumentException(
                    "durationMinutes must be one of: 15, 30, 45, 60, 90, 120");
        }
    }
}

package com.ainexus.hospital.patient.service;

import com.ainexus.hospital.patient.audit.AppointmentAuditService;
import com.ainexus.hospital.patient.dto.request.ClinicalNotesRequest;
import com.ainexus.hospital.patient.dto.response.ClinicalNotesResponse;
import com.ainexus.hospital.patient.entity.Appointment;
import com.ainexus.hospital.patient.entity.AppointmentStatus;
import com.ainexus.hospital.patient.entity.ClinicalNotes;
import com.ainexus.hospital.patient.exception.AppointmentNotFoundException;
import com.ainexus.hospital.patient.exception.ConflictException;
import com.ainexus.hospital.patient.exception.ForbiddenException;
import com.ainexus.hospital.patient.mapper.ClinicalNotesMapper;
import com.ainexus.hospital.patient.repository.AppointmentRepository;
import com.ainexus.hospital.patient.repository.ClinicalNotesRepository;
import com.ainexus.hospital.patient.security.AuthContext;
import com.ainexus.hospital.patient.security.RoleGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * US6: Clinical notes management with role-based access control.
 *
 * HIPAA rules:
 * - Note content is encrypted by NotesEncryptionConverter at the JPA layer
 * - privateNotes is only returned to DOCTOR (own appointment) or ADMIN
 * - Note content MUST NEVER appear in logs
 */
@Service
public class ClinicalNotesService {

    private static final Set<AppointmentStatus> ALLOWED_STATUSES =
            Set.of(AppointmentStatus.IN_PROGRESS, AppointmentStatus.COMPLETED);

    private final ClinicalNotesRepository notesRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentAuditService auditService;
    private final ClinicalNotesMapper mapper;
    private final RoleGuard roleGuard;

    public ClinicalNotesService(ClinicalNotesRepository notesRepository,
                                 AppointmentRepository appointmentRepository,
                                 AppointmentAuditService auditService,
                                 ClinicalNotesMapper mapper,
                                 RoleGuard roleGuard) {
        this.notesRepository = notesRepository;
        this.appointmentRepository = appointmentRepository;
        this.auditService = auditService;
        this.mapper = mapper;
        this.roleGuard = roleGuard;
    }

    @Transactional
    public ClinicalNotesResponse upsertNotes(String appointmentId, ClinicalNotesRequest request) {
        AuthContext ctx = AuthContext.Holder.get();
        roleGuard.requireRoles("DOCTOR", "ADMIN");

        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new AppointmentNotFoundException(appointmentId));

        // Appointment must be IN_PROGRESS or COMPLETED
        if (!ALLOWED_STATUSES.contains(appointment.getStatus())) {
            throw new ConflictException(
                    "Clinical notes can only be added when appointment is IN_PROGRESS or COMPLETED.");
        }

        // DOCTOR can only write notes for own appointments
        if ("DOCTOR".equals(ctx.getRole()) && !ctx.getUserId().equals(appointment.getDoctorId())) {
            throw new ForbiddenException("Doctors can only add notes to their own appointments.");
        }

        ClinicalNotes notes = notesRepository.findById(appointmentId)
                .orElseGet(() -> ClinicalNotes.builder()
                        .appointmentId(appointmentId)
                        .createdBy(ctx.getUsername())
                        .followUpRequired(false)
                        .build());

        // Apply non-null fields (upsert semantics)
        if (request.chiefComplaint() != null) notes.setChiefComplaint(request.chiefComplaint());
        if (request.diagnosis() != null) notes.setDiagnosis(request.diagnosis());
        if (request.treatment() != null) notes.setTreatment(request.treatment());
        if (request.prescription() != null) notes.setPrescription(request.prescription());
        if (request.followUpRequired() != null) notes.setFollowUpRequired(request.followUpRequired());
        if (request.followUpDays() != null) notes.setFollowUpDays(request.followUpDays());
        if (request.privateNotes() != null) notes.setPrivateNotes(request.privateNotes());

        notesRepository.save(notes);

        // Audit (no PHI — just the fact that notes were updated)
        auditService.writeAuditLog(appointmentId, "NOTES_UPSERT", appointment.getStatus(),
                appointment.getStatus(), ctx.getUsername(), "Clinical notes updated");

        boolean includePrivate = "DOCTOR".equals(ctx.getRole()) || "ADMIN".equals(ctx.getRole());
        return mapper.toResponse(notes, includePrivate);
    }

    @Transactional(readOnly = true)
    public ClinicalNotesResponse getNotes(String appointmentId) {
        AuthContext ctx = AuthContext.Holder.get();
        roleGuard.requireAuthenticated();

        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new AppointmentNotFoundException(appointmentId));

        // DOCTOR can only view notes for own appointments
        if ("DOCTOR".equals(ctx.getRole()) && !ctx.getUserId().equals(appointment.getDoctorId())) {
            throw new ForbiddenException("Doctors can only view notes for their own appointments.");
        }

        ClinicalNotes notes = notesRepository.findById(appointmentId)
                .orElseThrow(() -> new AppointmentNotFoundException(
                        "No clinical notes found for appointment: " + appointmentId));

        boolean includePrivate = "DOCTOR".equals(ctx.getRole()) || "ADMIN".equals(ctx.getRole());
        return mapper.toResponse(notes, includePrivate);
    }
}

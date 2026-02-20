package com.ainexus.hospital.patient.audit;

import com.ainexus.hospital.patient.entity.AppointmentAuditLog;
import com.ainexus.hospital.patient.entity.AppointmentStatus;
import com.ainexus.hospital.patient.repository.AppointmentAuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Writes immutable audit log entries for all appointment state changes.
 * Runs in the MANDATORY transaction context of the calling service method.
 *
 * HIPAA: this class never logs PHI (no appointment content, only IDs + statuses).
 */
@Service
public class AppointmentAuditService {

    private final AppointmentAuditLogRepository auditLogRepository;

    public AppointmentAuditService(AppointmentAuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void writeAuditLog(String appointmentId,
                              String action,
                              AppointmentStatus fromStatus,
                              AppointmentStatus toStatus,
                              String performedBy,
                              String details) {
        AppointmentAuditLog log = AppointmentAuditLog.builder()
                .appointmentId(appointmentId)
                .action(action)
                .fromStatus(fromStatus != null ? fromStatus.name() : null)
                .toStatus(toStatus.name())
                .performedBy(performedBy)
                .performedAt(OffsetDateTime.now())
                .details(details)
                .build();

        auditLogRepository.save(log);
    }
}

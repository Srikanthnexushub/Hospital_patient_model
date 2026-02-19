package com.ainexus.hospital.patient.audit;

import com.ainexus.hospital.patient.entity.PatientAuditLog;
import com.ainexus.hospital.patient.repository.PatientAuditLogRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Writes immutable audit log entries for every patient write operation.
 *
 * IMPORTANT: This service must always be called within an active @Transactional
 * scope so the audit log and patient record are committed atomically.
 *
 * PHI restriction: changedFields contains field NAMES only — never field values.
 */
@Service
public class AuditService {

    private final PatientAuditLogRepository auditLogRepository;

    public AuditService(PatientAuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Record a patient operation in the audit log.
     *
     * @param operation    One of: REGISTER, UPDATE, DEACTIVATE, ACTIVATE
     * @param patientId    The affected patient's ID (only PHI allowed in audit log)
     * @param performedBy  The username of the authenticated staff member
     * @param changedFields List of field names modified (UPDATE only; null for other operations)
     */
    public void writeAuditLog(String operation,
                               String patientId,
                               String performedBy,
                               List<String> changedFields) {
        PatientAuditLog entry = PatientAuditLog.builder()
                .timestamp(OffsetDateTime.now())
                .operation(operation)
                .patientId(patientId)
                .performedBy(performedBy)
                .changedFields(changedFields != null ? changedFields.toArray(new String[0]) : null)
                .build();

        auditLogRepository.save(entry);
    }
}

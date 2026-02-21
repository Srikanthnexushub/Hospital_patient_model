package com.ainexus.hospital.patient.audit;

import com.ainexus.hospital.patient.entity.EmrAuditLog;
import com.ainexus.hospital.patient.repository.EmrAuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Writes immutable audit log entries for all EMR entity mutations.
 * Runs in the MANDATORY transaction context of the calling service method.
 *
 * HIPAA: details column stores only field names or action context (e.g. "status changed
 * from ACTIVE to RESOLVED"), never actual clinical values.
 */
@Service
public class EmrAuditService {

    private final EmrAuditLogRepository auditLogRepository;

    public EmrAuditService(EmrAuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * @param entityType  one of: VITAL, PROBLEM, MEDICATION, ALLERGY
     * @param entityId    string form of the entity PK
     * @param patientId   denormalised patient ID for fast filtering
     * @param action      one of: CREATE, UPDATE, DISCONTINUE, RESOLVE, DELETE
     * @param performedBy username from auth context
     * @param details     field names or status transition context — no PHI values
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void writeAuditLog(String entityType,
                               String entityId,
                               String patientId,
                               String action,
                               String performedBy,
                               String details) {
        EmrAuditLog entry = EmrAuditLog.builder()
                .entityType(entityType)
                .entityId(entityId)
                .patientId(patientId)
                .action(action)
                .performedBy(performedBy)
                .performedAt(OffsetDateTime.now())
                .details(details)
                .build();

        auditLogRepository.save(entry);
    }
}

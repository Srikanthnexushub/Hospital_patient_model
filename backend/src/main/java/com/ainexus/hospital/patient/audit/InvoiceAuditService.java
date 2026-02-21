package com.ainexus.hospital.patient.audit;

import com.ainexus.hospital.patient.entity.InvoiceAuditLog;
import com.ainexus.hospital.patient.entity.InvoiceStatus;
import com.ainexus.hospital.patient.repository.InvoiceAuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Writes immutable audit log entries for all invoice state changes.
 * Runs in the MANDATORY transaction context of the calling service method.
 *
 * HIPAA: this class never logs PHI (no invoice amounts or patient data, only IDs + statuses).
 */
@Service
public class InvoiceAuditService {

    private final InvoiceAuditLogRepository auditLogRepository;

    public InvoiceAuditService(InvoiceAuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void writeAuditLog(String invoiceId,
                              String action,
                              InvoiceStatus fromStatus,
                              InvoiceStatus toStatus,
                              String performedBy,
                              String details) {
        InvoiceAuditLog entry = InvoiceAuditLog.builder()
                .invoiceId(invoiceId)
                .action(action)
                .fromStatus(fromStatus != null ? fromStatus.name() : null)
                .toStatus(toStatus.name())
                .performedBy(performedBy)
                .performedAt(OffsetDateTime.now())
                .details(details)
                .build();

        auditLogRepository.save(entry);
    }
}

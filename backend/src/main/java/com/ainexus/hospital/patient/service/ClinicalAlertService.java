package com.ainexus.hospital.patient.service;

import com.ainexus.hospital.patient.audit.EmrAuditService;
import com.ainexus.hospital.patient.dto.ClinicalAlertResponse;
import com.ainexus.hospital.patient.entity.*;
import com.ainexus.hospital.patient.exception.ResourceNotFoundException;
import com.ainexus.hospital.patient.mapper.ClinicalAlertMapper;
import com.ainexus.hospital.patient.repository.ClinicalAlertRepository;
import com.ainexus.hospital.patient.repository.PatientRepository;
import com.ainexus.hospital.patient.security.AuthContext;
import com.ainexus.hospital.patient.security.RoleGuard;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Core shared service for all clinical alert operations.
 *
 * <p>Called by LabOrderService, News2Service, and DrugInteractionService to create alerts,
 * and directly by ClinicalAlertController for acknowledge / dismiss / list operations.
 */
@Service
public class ClinicalAlertService {

    private static final Set<String> ALERT_VIEWERS = Set.of("DOCTOR", "NURSE", "ADMIN");

    private final ClinicalAlertRepository alertRepository;
    private final PatientRepository patientRepository;
    private final ClinicalAlertMapper alertMapper;
    private final EmrAuditService emrAuditService;
    private final RoleGuard roleGuard;

    public ClinicalAlertService(ClinicalAlertRepository alertRepository,
                                PatientRepository patientRepository,
                                ClinicalAlertMapper alertMapper,
                                EmrAuditService emrAuditService,
                                RoleGuard roleGuard) {
        this.alertRepository = alertRepository;
        this.patientRepository = patientRepository;
        this.alertMapper = alertMapper;
        this.emrAuditService = emrAuditService;
        this.roleGuard = roleGuard;
    }

    // -------------------------------------------------------------------------
    // Alert creation (called internally by other services, no RBAC check needed)
    // -------------------------------------------------------------------------

    /**
     * Creates a new clinical alert. For NEWS2 alert types, any existing ACTIVE alert
     * of the same type for this patient is auto-dismissed before the new one is saved
     * (deduplication rule).
     *
     * <p>Must be called within an active transaction (Propagation.REQUIRED).
     */
    @Transactional
    public ClinicalAlert createAlert(String patientId,
                                     AlertType alertType,
                                     AlertSeverity severity,
                                     String title,
                                     String description,
                                     String source,
                                     String triggerValue) {
        String actor = currentUsername();

        // NEWS2 deduplication: dismiss any existing ACTIVE alert of the same type
        if (alertType.isNews2Type()) {
            alertRepository.findByPatientIdAndAlertTypeAndStatus(patientId, alertType, AlertStatus.ACTIVE)
                    .ifPresent(existing -> {
                        existing.setStatus(AlertStatus.DISMISSED);
                        existing.setDismissedAt(OffsetDateTime.now());
                        existing.setDismissReason("Auto-dismissed — replaced by updated score");
                        alertRepository.save(existing);
                    });
        }

        ClinicalAlert alert = ClinicalAlert.builder()
                .patientId(patientId)
                .alertType(alertType)
                .severity(severity)
                .title(title)
                .description(description)
                .source(source)
                .triggerValue(triggerValue)
                .status(AlertStatus.ACTIVE)
                .build();

        ClinicalAlert saved = alertRepository.save(alert);

        emrAuditService.writeAuditLog(
                "CLINICAL_ALERT", saved.getId().toString(), patientId,
                "CREATE", actor,
                "type=" + alertType + " severity=" + severity);

        return saved;
    }

    // -------------------------------------------------------------------------
    // Acknowledge
    // -------------------------------------------------------------------------

    @Transactional
    public ClinicalAlertResponse acknowledge(UUID alertId) {
        roleGuard.requireRoles("DOCTOR", "NURSE", "ADMIN");
        AuthContext ctx = AuthContext.Holder.get();

        ClinicalAlert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new ResourceNotFoundException("Alert not found: " + alertId));

        alert.setStatus(AlertStatus.ACKNOWLEDGED);
        alert.setAcknowledgedAt(OffsetDateTime.now());
        alert.setAcknowledgedBy(ctx.getUsername());
        ClinicalAlert saved = alertRepository.save(alert);

        emrAuditService.writeAuditLog(
                "CLINICAL_ALERT", alertId.toString(), alert.getPatientId(),
                "ACKNOWLEDGE", ctx.getUsername(), "acknowledged");

        return enrichWithPatientName(alertMapper.toResponse(saved));
    }

    // -------------------------------------------------------------------------
    // Dismiss
    // -------------------------------------------------------------------------

    @Transactional
    public ClinicalAlertResponse dismiss(UUID alertId, String reason) {
        roleGuard.requireRoles("DOCTOR", "NURSE", "ADMIN");
        AuthContext ctx = AuthContext.Holder.get();

        ClinicalAlert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new ResourceNotFoundException("Alert not found: " + alertId));

        alert.setStatus(AlertStatus.DISMISSED);
        alert.setDismissedAt(OffsetDateTime.now());
        alert.setDismissReason(reason);
        ClinicalAlert saved = alertRepository.save(alert);

        emrAuditService.writeAuditLog(
                "CLINICAL_ALERT", alertId.toString(), alert.getPatientId(),
                "DISMISS", ctx.getUsername(), "dismissed");

        return enrichWithPatientName(alertMapper.toResponse(saved));
    }

    // -------------------------------------------------------------------------
    // Per-patient alert feed
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<ClinicalAlertResponse> getPatientAlerts(String patientId,
                                                         AlertStatus status,
                                                         AlertSeverity severity,
                                                         Pageable pageable) {
        roleGuard.requireRoles(ALERT_VIEWERS);
        return alertRepository.findByPatientIdFiltered(patientId, status, severity, pageable)
                .map(alertMapper::toResponse);
    }

    // -------------------------------------------------------------------------
    // Global alert feed (DOCTOR-scoped or full for ADMIN/NURSE)
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<ClinicalAlertResponse> getGlobalAlerts(AlertStatus status,
                                                        AlertSeverity severity,
                                                        Pageable pageable) {
        roleGuard.requireRoles(ALERT_VIEWERS);
        AuthContext ctx = AuthContext.Holder.get();

        String doctorId = "DOCTOR".equals(ctx.getRole()) ? ctx.getUserId() : null;

        Page<ClinicalAlert> page = alertRepository.findGlobalFiltered(status, severity, doctorId, pageable);

        // Batch-load patient names to avoid N+1
        Set<String> patientIds = page.map(ClinicalAlert::getPatientId).toSet();
        Map<String, String> nameMap = patientRepository.findAllById(patientIds).stream()
                .collect(Collectors.toMap(
                        Patient::getPatientId,
                        p -> p.getFirstName() + " " + p.getLastName()));

        return page.map(alert -> {
            ClinicalAlertResponse base = alertMapper.toResponse(alert);
            String name = nameMap.get(alert.getPatientId());
            return new ClinicalAlertResponse(
                    base.id(), base.patientId(), name,
                    base.alertType(), base.severity(),
                    base.title(), base.description(), base.source(), base.triggerValue(),
                    base.status(), base.createdAt(),
                    base.acknowledgedBy(), base.acknowledgedAt(),
                    base.dismissReason(), base.dismissedAt());
        });
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private ClinicalAlertResponse enrichWithPatientName(ClinicalAlertResponse response) {
        String name = patientRepository.findById(response.patientId())
                .map(p -> p.getFirstName() + " " + p.getLastName())
                .orElse(null);
        return new ClinicalAlertResponse(
                response.id(), response.patientId(), name,
                response.alertType(), response.severity(),
                response.title(), response.description(), response.source(), response.triggerValue(),
                response.status(), response.createdAt(),
                response.acknowledgedBy(), response.acknowledgedAt(),
                response.dismissReason(), response.dismissedAt());
    }

    private String currentUsername() {
        AuthContext ctx = AuthContext.Holder.get();
        return ctx != null ? ctx.getUsername() : "system";
    }
}

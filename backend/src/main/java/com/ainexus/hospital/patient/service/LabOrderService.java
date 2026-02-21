package com.ainexus.hospital.patient.service;

import com.ainexus.hospital.patient.audit.EmrAuditService;
import com.ainexus.hospital.patient.dto.*;
import com.ainexus.hospital.patient.entity.*;
import com.ainexus.hospital.patient.exception.ConflictException;
import com.ainexus.hospital.patient.exception.ResourceNotFoundException;
import com.ainexus.hospital.patient.mapper.LabOrderMapper;
import com.ainexus.hospital.patient.mapper.LabResultMapper;
import com.ainexus.hospital.patient.repository.LabOrderRepository;
import com.ainexus.hospital.patient.repository.LabResultRepository;
import com.ainexus.hospital.patient.repository.PatientRepository;
import com.ainexus.hospital.patient.security.AuthContext;
import com.ainexus.hospital.patient.security.RoleGuard;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class LabOrderService {

    private final LabOrderRepository labOrderRepository;
    private final LabResultRepository labResultRepository;
    private final PatientRepository patientRepository;
    private final ClinicalAlertService clinicalAlertService;
    private final EmrAuditService emrAuditService;
    private final LabOrderMapper labOrderMapper;
    private final LabResultMapper labResultMapper;
    private final RoleGuard roleGuard;

    public LabOrderService(LabOrderRepository labOrderRepository,
                           LabResultRepository labResultRepository,
                           PatientRepository patientRepository,
                           ClinicalAlertService clinicalAlertService,
                           EmrAuditService emrAuditService,
                           LabOrderMapper labOrderMapper,
                           LabResultMapper labResultMapper,
                           RoleGuard roleGuard) {
        this.labOrderRepository = labOrderRepository;
        this.labResultRepository = labResultRepository;
        this.patientRepository = patientRepository;
        this.clinicalAlertService = clinicalAlertService;
        this.emrAuditService = emrAuditService;
        this.labOrderMapper = labOrderMapper;
        this.labResultMapper = labResultMapper;
        this.roleGuard = roleGuard;
    }

    // -------------------------------------------------------------------------
    // Order creation
    // -------------------------------------------------------------------------

    @Transactional
    public LabOrderResponse createLabOrder(String patientId, CreateLabOrderRequest request) {
        roleGuard.requireRoles("DOCTOR", "ADMIN");
        AuthContext ctx = AuthContext.Holder.get();

        if (!patientRepository.existsById(patientId)) {
            throw new ResourceNotFoundException("Patient not found: " + patientId);
        }

        LabOrder order = LabOrder.builder()
                .patientId(patientId)
                .testName(request.testName())
                .testCode(request.testCode())
                .category(request.category())
                .priority(request.priority() != null ? request.priority() : LabOrderPriority.ROUTINE)
                .status(LabOrderStatus.PENDING)
                .orderedBy(ctx.getUsername())
                .appointmentId(request.appointmentId())
                .notes(request.notes())
                .build();

        LabOrder saved = labOrderRepository.save(order);

        emrAuditService.writeAuditLog(
                "LAB_ORDER", saved.getId().toString(), patientId,
                "CREATE", ctx.getUsername(),
                "test=" + request.testName() + " category=" + request.category());

        return labOrderMapper.toResponse(saved);
    }

    // -------------------------------------------------------------------------
    // Record result (advances order → RESULTED, fires alert if abnormal/critical)
    // -------------------------------------------------------------------------

    @Transactional
    public LabResultResponse recordLabResult(UUID orderId, RecordLabResultRequest request) {
        roleGuard.requireRoles("NURSE", "DOCTOR", "ADMIN");
        AuthContext ctx = AuthContext.Holder.get();

        LabOrder order = labOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Lab order not found: " + orderId));

        if (order.getStatus() == LabOrderStatus.RESULTED || order.getStatus() == LabOrderStatus.CANCELLED) {
            throw new ConflictException("Lab order is already " + order.getStatus());
        }

        LabResult result = LabResult.builder()
                .orderId(orderId)
                .patientId(order.getPatientId())
                .value(request.value())
                .unit(request.unit())
                .referenceRangeLow(request.referenceRangeLow())
                .referenceRangeHigh(request.referenceRangeHigh())
                .interpretation(request.interpretation())
                .resultNotes(request.resultNotes())
                .resultedBy(ctx.getUsername())
                .build();

        LabResult savedResult = labResultRepository.save(result);

        // Advance order status
        order.setStatus(LabOrderStatus.RESULTED);
        labOrderRepository.save(order);

        emrAuditService.writeAuditLog(
                "LAB_RESULT", savedResult.getId().toString(), order.getPatientId(),
                "CREATE", ctx.getUsername(),
                "interpretation=" + request.interpretation());

        // Auto-create clinical alert based on interpretation
        ClinicalAlert alert = null;
        if (request.interpretation().isCritical()) {
            alert = clinicalAlertService.createAlert(
                    order.getPatientId(),
                    AlertType.LAB_CRITICAL,
                    AlertSeverity.CRITICAL,
                    "Critical Lab Result: " + order.getTestName(),
                    "Result value " + request.value() + (request.unit() != null ? " " + request.unit() : "")
                            + " — interpretation: " + request.interpretation(),
                    "LabOrderService",
                    request.value());
        } else if (request.interpretation().isOutOfRange()) {
            alert = clinicalAlertService.createAlert(
                    order.getPatientId(),
                    AlertType.LAB_ABNORMAL,
                    AlertSeverity.WARNING,
                    "Abnormal Lab Result: " + order.getTestName(),
                    "Result value " + request.value() + (request.unit() != null ? " " + request.unit() : "")
                            + " — interpretation: " + request.interpretation(),
                    "LabOrderService",
                    request.value());
        }

        LabResultResponse base = labResultMapper.toResponse(savedResult);
        boolean alertCreated = alert != null;
        UUID alertId = alertCreated ? alert.getId() : null;

        return new LabResultResponse(
                base.id(), base.orderId(), base.patientId(),
                order.getTestName(),
                base.value(), base.unit(),
                base.referenceRangeLow(), base.referenceRangeHigh(),
                base.interpretation(), base.resultNotes(),
                base.resultedBy(), base.resultedAt(),
                alertCreated, alertId);
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<LabOrderSummaryResponse> getLabOrders(String patientId,
                                                       LabOrderStatus status,
                                                       Pageable pageable) {
        roleGuard.requireRoles("DOCTOR", "NURSE", "ADMIN");

        Page<LabOrder> page = (status != null)
                ? labOrderRepository.findByPatientIdAndStatusOrderByOrderedAtDesc(patientId, status, pageable)
                : labOrderRepository.findByPatientIdOrderByOrderedAtDesc(patientId, pageable);

        return page.map(order -> {
            boolean hasResult = labResultRepository.findByOrderId(order.getId()).isPresent();
            LabOrderSummaryResponse base = labOrderMapper.toSummary(order);
            return new LabOrderSummaryResponse(
                    base.id(), base.patientId(), base.testName(),
                    base.category(), base.priority(), base.status(),
                    base.orderedBy(), base.orderedAt(), hasResult);
        });
    }

    @Transactional(readOnly = true)
    public Page<LabResultResponse> getLabResults(String patientId, Pageable pageable) {
        roleGuard.requireRoles("DOCTOR", "NURSE", "ADMIN");
        return labResultRepository.findByPatientIdOrderByResultedAtDesc(patientId, pageable)
                .map(labResultMapper::toResponse);
    }
}

package com.ainexus.hospital.patient.unit.service;

import com.ainexus.hospital.patient.audit.EmrAuditService;
import com.ainexus.hospital.patient.dto.CreateLabOrderRequest;
import com.ainexus.hospital.patient.dto.LabOrderResponse;
import com.ainexus.hospital.patient.dto.LabResultResponse;
import com.ainexus.hospital.patient.dto.RecordLabResultRequest;
import com.ainexus.hospital.patient.entity.*;
import com.ainexus.hospital.patient.exception.ConflictException;
import com.ainexus.hospital.patient.exception.ForbiddenException;
import com.ainexus.hospital.patient.exception.ResourceNotFoundException;
import com.ainexus.hospital.patient.mapper.LabOrderMapper;
import com.ainexus.hospital.patient.mapper.LabResultMapper;
import com.ainexus.hospital.patient.repository.LabOrderRepository;
import com.ainexus.hospital.patient.repository.LabResultRepository;
import com.ainexus.hospital.patient.repository.PatientRepository;
import com.ainexus.hospital.patient.security.AuthContext;
import com.ainexus.hospital.patient.security.RoleGuard;
import com.ainexus.hospital.patient.service.ClinicalAlertService;
import com.ainexus.hospital.patient.service.LabOrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LabOrderServiceTest {

    @Mock private LabOrderRepository labOrderRepository;
    @Mock private LabResultRepository labResultRepository;
    @Mock private PatientRepository patientRepository;
    @Mock private ClinicalAlertService clinicalAlertService;
    @Mock private EmrAuditService emrAuditService;
    @Mock private LabOrderMapper labOrderMapper;
    @Mock private LabResultMapper labResultMapper;

    private LabOrderService labOrderService;

    private static final String PATIENT_ID = "P2025001";
    private static final UUID ORDER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        labOrderService = new LabOrderService(
                labOrderRepository, labResultRepository, patientRepository,
                clinicalAlertService, emrAuditService,
                labOrderMapper, labResultMapper, new RoleGuard());
    }

    @AfterEach
    void clearContext() {
        AuthContext.Holder.clear();
    }

    private void setAuth(String role) {
        AuthContext.Holder.set(new AuthContext("uid001", role.toLowerCase() + "1", role));
    }

    // -------------------------------------------------------------------------
    // createLabOrder
    // -------------------------------------------------------------------------

    @Test
    void createLabOrder_savedWithPendingStatusAndOrderedByFromAuth() {
        setAuth("DOCTOR");
        when(patientRepository.existsById(PATIENT_ID)).thenReturn(true);

        LabOrder saved = stubOrder(LabOrderStatus.PENDING);
        when(labOrderRepository.save(any(LabOrder.class))).thenReturn(saved);
        when(labOrderMapper.toResponse(saved)).thenReturn(stubOrderResponse());

        LabOrderResponse response = labOrderService.createLabOrder(PATIENT_ID, stubCreateRequest());

        assertThat(response).isNotNull();
        verify(labOrderRepository).save(argThat(order ->
                order.getStatus() == LabOrderStatus.PENDING
                && order.getOrderedBy().equals("doctor1")
                && order.getPatientId().equals(PATIENT_ID)));
    }

    @Test
    void createLabOrder_patientNotFound_throwsResourceNotFoundException() {
        setAuth("DOCTOR");
        when(patientRepository.existsById(PATIENT_ID)).thenReturn(false);
        assertThatThrownBy(() -> labOrderService.createLabOrder(PATIENT_ID, stubCreateRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createLabOrder_receptionistRole_throwsForbiddenException() {
        setAuth("RECEPTIONIST");
        assertThatThrownBy(() -> labOrderService.createLabOrder(PATIENT_ID, stubCreateRequest()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void createLabOrder_nurseRole_throwsForbiddenException() {
        setAuth("NURSE");
        assertThatThrownBy(() -> labOrderService.createLabOrder(PATIENT_ID, stubCreateRequest()))
                .isInstanceOf(ForbiddenException.class);
    }

    // -------------------------------------------------------------------------
    // recordLabResult
    // -------------------------------------------------------------------------

    @Test
    void recordLabResult_criticalHigh_callsCreateAlertWithCriticalSeverity() {
        setAuth("NURSE");
        LabOrder order = stubOrder(LabOrderStatus.PENDING);
        when(labOrderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        LabResult savedResult = stubResult(LabResultInterpretation.CRITICAL_HIGH);
        when(labResultRepository.save(any(LabResult.class))).thenReturn(savedResult);
        when(labOrderRepository.save(any(LabOrder.class))).thenReturn(order);

        ClinicalAlert alert = new ClinicalAlert();
        alert.setId(UUID.randomUUID());
        when(clinicalAlertService.createAlert(
                eq(PATIENT_ID), eq(AlertType.LAB_CRITICAL), eq(AlertSeverity.CRITICAL),
                any(), any(), any(), any())).thenReturn(alert);

        LabResultResponse stubResp = stubResultResponse(false, null);
        when(labResultMapper.toResponse(savedResult)).thenReturn(stubResp);

        LabResultResponse response = labOrderService.recordLabResult(ORDER_ID,
                new RecordLabResultRequest("7.2", "mmol/L", null, null,
                        LabResultInterpretation.CRITICAL_HIGH, null));

        verify(clinicalAlertService).createAlert(
                eq(PATIENT_ID), eq(AlertType.LAB_CRITICAL), eq(AlertSeverity.CRITICAL),
                any(), any(), any(), any());
        assertThat(response.alertCreated()).isTrue();
        assertThat(response.alertId()).isNotNull();
    }

    @Test
    void recordLabResult_high_callsCreateAlertWithWarningSeverity() {
        setAuth("DOCTOR");
        LabOrder order = stubOrder(LabOrderStatus.PENDING);
        when(labOrderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        LabResult savedResult = stubResult(LabResultInterpretation.HIGH);
        when(labResultRepository.save(any(LabResult.class))).thenReturn(savedResult);
        when(labOrderRepository.save(any(LabOrder.class))).thenReturn(order);

        ClinicalAlert alert = new ClinicalAlert();
        alert.setId(UUID.randomUUID());
        when(clinicalAlertService.createAlert(
                eq(PATIENT_ID), eq(AlertType.LAB_ABNORMAL), eq(AlertSeverity.WARNING),
                any(), any(), any(), any())).thenReturn(alert);

        LabResultResponse stubResp = stubResultResponse(false, null);
        when(labResultMapper.toResponse(savedResult)).thenReturn(stubResp);

        labOrderService.recordLabResult(ORDER_ID,
                new RecordLabResultRequest("150", "mg/dL", null, null,
                        LabResultInterpretation.HIGH, null));

        verify(clinicalAlertService).createAlert(
                eq(PATIENT_ID), eq(AlertType.LAB_ABNORMAL), eq(AlertSeverity.WARNING),
                any(), any(), any(), any());
    }

    @Test
    void recordLabResult_normal_doesNotCreateAlert() {
        setAuth("NURSE");
        LabOrder order = stubOrder(LabOrderStatus.PENDING);
        when(labOrderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        LabResult savedResult = stubResult(LabResultInterpretation.NORMAL);
        when(labResultRepository.save(any(LabResult.class))).thenReturn(savedResult);
        when(labOrderRepository.save(any(LabOrder.class))).thenReturn(order);

        LabResultResponse stubResp = stubResultResponse(false, null);
        when(labResultMapper.toResponse(savedResult)).thenReturn(stubResp);

        labOrderService.recordLabResult(ORDER_ID,
                new RecordLabResultRequest("5.2", "g/dL", null, null,
                        LabResultInterpretation.NORMAL, null));

        verify(clinicalAlertService, never()).createAlert(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void recordLabResult_alreadyResulted_throwsConflictException() {
        setAuth("NURSE");
        LabOrder order = stubOrder(LabOrderStatus.RESULTED);
        when(labOrderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> labOrderService.recordLabResult(ORDER_ID,
                new RecordLabResultRequest("5.2", null, null, null,
                        LabResultInterpretation.NORMAL, null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void recordLabResult_orderNotFound_throwsResourceNotFoundException() {
        setAuth("NURSE");
        when(labOrderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> labOrderService.recordLabResult(ORDER_ID,
                new RecordLabResultRequest("5.2", null, null, null,
                        LabResultInterpretation.NORMAL, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void recordLabResult_receptionistRole_throwsForbiddenException() {
        setAuth("RECEPTIONIST");
        assertThatThrownBy(() -> labOrderService.recordLabResult(ORDER_ID,
                new RecordLabResultRequest("5.2", null, null, null,
                        LabResultInterpretation.NORMAL, null)))
                .isInstanceOf(ForbiddenException.class);
    }

    // -------------------------------------------------------------------------
    // listOrders
    // -------------------------------------------------------------------------

    @Test
    void listOrders_withStatusFilter_returnsOnlyMatchingOrders() {
        setAuth("DOCTOR");
        Pageable pageable = PageRequest.of(0, 20);
        LabOrder order = stubOrder(LabOrderStatus.PENDING);
        when(labOrderRepository.findByPatientIdAndStatusOrderByOrderedAtDesc(PATIENT_ID, LabOrderStatus.PENDING, pageable))
                .thenReturn(new PageImpl<>(List.of(order)));
        when(labOrderMapper.toSummary(any())).thenReturn(stubOrderSummary());

        var result = labOrderService.getLabOrders(PATIENT_ID, LabOrderStatus.PENDING, pageable);
        assertThat(result.getContent()).hasSize(1);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private CreateLabOrderRequest stubCreateRequest() {
        return new CreateLabOrderRequest("CBC", "CBC001", LabOrderCategory.HEMATOLOGY,
                null, LabOrderPriority.ROUTINE, null);
    }

    private LabOrder stubOrder(LabOrderStatus status) {
        LabOrder o = new LabOrder();
        o.setId(ORDER_ID);
        o.setPatientId(PATIENT_ID);
        o.setTestName("CBC");
        o.setCategory(LabOrderCategory.HEMATOLOGY);
        o.setPriority(LabOrderPriority.ROUTINE);
        o.setStatus(status);
        o.setOrderedBy("doctor1");
        o.setOrderedAt(OffsetDateTime.now());
        return o;
    }

    private LabResult stubResult(LabResultInterpretation interpretation) {
        LabResult r = new LabResult();
        r.setId(UUID.randomUUID());
        r.setOrderId(ORDER_ID);
        r.setPatientId(PATIENT_ID);
        r.setValue("7.2");
        r.setInterpretation(interpretation);
        r.setResultedBy("nurse1");
        r.setResultedAt(OffsetDateTime.now());
        return r;
    }

    private LabOrderResponse stubOrderResponse() {
        return new LabOrderResponse(ORDER_ID, PATIENT_ID, "CBC", "CBC001",
                LabOrderCategory.HEMATOLOGY, LabOrderPriority.ROUTINE, LabOrderStatus.PENDING,
                "doctor1", OffsetDateTime.now(), null, null, null);
    }

    private com.ainexus.hospital.patient.dto.LabOrderSummaryResponse stubOrderSummary() {
        return new com.ainexus.hospital.patient.dto.LabOrderSummaryResponse(
                ORDER_ID, PATIENT_ID, "CBC",
                LabOrderCategory.HEMATOLOGY, LabOrderPriority.ROUTINE, LabOrderStatus.PENDING,
                "doctor1", OffsetDateTime.now(), false);
    }

    private LabResultResponse stubResultResponse(boolean alertCreated, UUID alertId) {
        return new LabResultResponse(UUID.randomUUID(), ORDER_ID, PATIENT_ID,
                "CBC", "7.2", "mmol/L", null, null,
                LabResultInterpretation.CRITICAL_HIGH, null,
                "nurse1", OffsetDateTime.now(), alertCreated, alertId);
    }
}

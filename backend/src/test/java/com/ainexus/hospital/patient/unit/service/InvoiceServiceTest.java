package com.ainexus.hospital.patient.unit.service;

import com.ainexus.hospital.patient.audit.InvoiceAuditService;
import com.ainexus.hospital.patient.dto.request.CreateInvoiceRequest;
import com.ainexus.hospital.patient.dto.request.LineItemRequest;
import com.ainexus.hospital.patient.dto.response.InvoiceDetailResponse;
import com.ainexus.hospital.patient.entity.*;
import com.ainexus.hospital.patient.exception.ForbiddenException;
import com.ainexus.hospital.patient.exception.InvalidInvoiceTransitionException;
import com.ainexus.hospital.patient.exception.ResourceNotFoundException;
import com.ainexus.hospital.patient.mapper.InvoiceMapper;
import com.ainexus.hospital.patient.repository.*;
import com.ainexus.hospital.patient.security.AuthContext;
import com.ainexus.hospital.patient.security.RoleGuard;
import com.ainexus.hospital.patient.service.InvoiceIdGeneratorService;
import com.ainexus.hospital.patient.service.InvoiceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Records are final and cannot be Mockito-mocked; use a real stub instance instead.

@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest {

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private InvoiceLineItemRepository lineItemRepository;
    @Mock private InvoicePaymentRepository paymentRepository;
    @Mock private InvoiceAuditLogRepository auditLogRepository;
    @Mock private InvoiceIdGeneratorService idGeneratorService;
    @Mock private InvoiceAuditService auditService;
    @Mock private InvoiceMapper mapper;
    @Mock private AppointmentRepository appointmentRepository;
    @Mock private PatientRepository patientRepository;
    @Mock private HospitalUserRepository hospitalUserRepository;

    private InvoiceService invoiceService;

    private static final String APPT_ID    = "APT2025001";
    private static final String PATIENT_ID = "P2025001";
    private static final String DOCTOR_ID  = "U2025001";
    private static final String INVOICE_ID = "INV2026000001";

    @BeforeEach
    void setUp() {
        RoleGuard roleGuard = new RoleGuard();
        invoiceService = new InvoiceService(
                invoiceRepository, lineItemRepository, paymentRepository, auditLogRepository,
                idGeneratorService, auditService, mapper, appointmentRepository,
                patientRepository, hospitalUserRepository, roleGuard);
        ReflectionTestUtils.setField(invoiceService, "taxRate", BigDecimal.ZERO);
    }

    @AfterEach
    void clearContext() {
        AuthContext.Holder.clear();
    }

    private static InvoiceDetailResponse stubDetailResponse() {
        return new InvoiceDetailResponse(
                INVOICE_ID, APPT_ID, "2025-06-15", PATIENT_ID, null,
                DOCTOR_ID, null, InvoiceStatus.DRAFT,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                null, null, 0, null, null, null, null,
                List.of(), List.of());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void setAuth(String role) {
        AuthContext.Holder.set(new AuthContext("userId001", role.toLowerCase() + "1", role));
    }

    private Appointment stubAppointment() {
        Appointment appt = Appointment.builder()
                .appointmentId(APPT_ID)
                .patientId(PATIENT_ID)
                .doctorId(DOCTOR_ID)
                .appointmentDate(LocalDate.of(2025, 6, 15))
                .build();
        when(appointmentRepository.findById(APPT_ID)).thenReturn(Optional.of(appt));
        return appt;
    }

    private CreateInvoiceRequest twoLineItemRequest() {
        // Line 1: qty=1, price=200.00 → 200.00
        // Line 2: qty=2, price=150.00 → 300.00
        // Total = 500.00, discount=10% → discount=50.00, net=450.00, tax=0, due=450.00
        return new CreateInvoiceRequest(
                APPT_ID,
                List.of(
                        new LineItemRequest("General Consultation", 1, new BigDecimal("200.00"), "CONS001"),
                        new LineItemRequest("Blood Panel", 2, new BigDecimal("150.00"), "LAB002")
                ),
                new BigDecimal("10.00"),
                null
        );
    }

    // ── US1: createInvoice ────────────────────────────────────────────────────

    @Test
    void createInvoice_receptionist_computesCorrectMonetaryTotals() {
        setAuth("RECEPTIONIST");
        stubAppointment();
        when(invoiceRepository.existsByAppointmentId(APPT_ID)).thenReturn(false);
        when(idGeneratorService.generateInvoiceId()).thenReturn(INVOICE_ID);
        when(invoiceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(lineItemRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        // Capture the saved invoice to verify monetary fields
        Invoice[] savedInvoice = new Invoice[1];
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> {
            savedInvoice[0] = inv.getArgument(0);
            return savedInvoice[0];
        });

        // mapper stub
        when(mapper.toDetailResponse(any(), any(), any(), any(), any(), any()))
                .thenReturn(stubDetailResponse());
        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.empty());
        when(hospitalUserRepository.findById(DOCTOR_ID)).thenReturn(Optional.empty());

        invoiceService.createInvoice(twoLineItemRequest());

        Invoice inv = savedInvoice[0];
        assertThat(inv.getTotalAmount()).isEqualByComparingTo("500.00");
        assertThat(inv.getDiscountPercent()).isEqualByComparingTo("10.00");
        assertThat(inv.getDiscountAmount()).isEqualByComparingTo("50.00");
        assertThat(inv.getNetAmount()).isEqualByComparingTo("450.00");
        assertThat(inv.getTaxAmount()).isEqualByComparingTo("0.00");
        assertThat(inv.getAmountDue()).isEqualByComparingTo("450.00");
        assertThat(inv.getStatus()).isEqualTo(InvoiceStatus.DRAFT);
        assertThat(inv.getInvoiceId()).isEqualTo(INVOICE_ID);

        verify(auditService).writeAuditLog(eq(INVOICE_ID), eq("CREATE"), isNull(),
                eq(InvoiceStatus.DRAFT), anyString(), isNull());
    }

    @Test
    void createInvoice_adminAllowed() {
        setAuth("ADMIN");
        stubAppointment();
        when(invoiceRepository.existsByAppointmentId(APPT_ID)).thenReturn(false);
        when(idGeneratorService.generateInvoiceId()).thenReturn(INVOICE_ID);
        when(invoiceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(lineItemRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toDetailResponse(any(), any(), any(), any(), any(), any()))
                .thenReturn(stubDetailResponse());
        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.empty());
        when(hospitalUserRepository.findById(DOCTOR_ID)).thenReturn(Optional.empty());

        invoiceService.createInvoice(twoLineItemRequest());
        verify(invoiceRepository).save(any());
    }

    @Test
    void createInvoice_nurseRole_throwsForbidden() {
        setAuth("NURSE");
        assertThatThrownBy(() -> invoiceService.createInvoice(twoLineItemRequest()))
                .isInstanceOf(ForbiddenException.class);
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    void createInvoice_doctorRole_throwsForbidden() {
        setAuth("DOCTOR");
        assertThatThrownBy(() -> invoiceService.createInvoice(twoLineItemRequest()))
                .isInstanceOf(ForbiddenException.class);
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    void createInvoice_appointmentNotFound_throws404() {
        setAuth("RECEPTIONIST");
        when(appointmentRepository.findById(APPT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> invoiceService.createInvoice(twoLineItemRequest()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(APPT_ID);
    }

    @Test
    void createInvoice_duplicateAppointment_throws409() {
        setAuth("RECEPTIONIST");
        stubAppointment();
        when(invoiceRepository.existsByAppointmentId(APPT_ID)).thenReturn(true);

        assertThatThrownBy(() -> invoiceService.createInvoice(twoLineItemRequest()))
                .isInstanceOf(InvalidInvoiceTransitionException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void createInvoice_noDiscount_dueTotalsEqualsNet() {
        setAuth("RECEPTIONIST");
        stubAppointment();
        when(invoiceRepository.existsByAppointmentId(APPT_ID)).thenReturn(false);
        when(idGeneratorService.generateInvoiceId()).thenReturn(INVOICE_ID);
        when(lineItemRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.empty());
        when(hospitalUserRepository.findById(DOCTOR_ID)).thenReturn(Optional.empty());
        when(mapper.toDetailResponse(any(), any(), any(), any(), any(), any()))
                .thenReturn(stubDetailResponse());

        Invoice[] saved = new Invoice[1];
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> {
            saved[0] = inv.getArgument(0);
            return saved[0];
        });

        CreateInvoiceRequest req = new CreateInvoiceRequest(
                APPT_ID,
                List.of(new LineItemRequest("Consultation", 1, new BigDecimal("300.00"), null)),
                null, null);
        invoiceService.createInvoice(req);

        assertThat(saved[0].getTotalAmount()).isEqualByComparingTo("300.00");
        assertThat(saved[0].getDiscountAmount()).isEqualByComparingTo("0.00");
        assertThat(saved[0].getNetAmount()).isEqualByComparingTo("300.00");
        assertThat(saved[0].getAmountDue()).isEqualByComparingTo("300.00");
    }
}

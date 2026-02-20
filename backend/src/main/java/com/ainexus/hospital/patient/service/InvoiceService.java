package com.ainexus.hospital.patient.service;

import com.ainexus.hospital.patient.audit.InvoiceAuditService;
import com.ainexus.hospital.patient.dto.request.CreateInvoiceRequest;
import com.ainexus.hospital.patient.dto.request.InvoiceStatusUpdateRequest;
import com.ainexus.hospital.patient.dto.request.LineItemRequest;
import com.ainexus.hospital.patient.dto.request.RecordPaymentRequest;
import com.ainexus.hospital.patient.dto.response.*;
import com.ainexus.hospital.patient.entity.*;
import com.ainexus.hospital.patient.exception.ForbiddenException;
import com.ainexus.hospital.patient.exception.InvoiceNotFoundException;
import com.ainexus.hospital.patient.exception.InvalidInvoiceTransitionException;
import com.ainexus.hospital.patient.exception.ResourceNotFoundException;
import com.ainexus.hospital.patient.mapper.InvoiceMapper;
import com.ainexus.hospital.patient.repository.*;
import com.ainexus.hospital.patient.security.AuthContext;
import com.ainexus.hospital.patient.security.RoleGuard;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class InvoiceService {

    // ── Valid cancel/write-off transitions ─────────────────────────────────────
    private static final Set<InvoiceStatus> CANCEL_FROM  = Set.of(InvoiceStatus.DRAFT, InvoiceStatus.ISSUED);
    private static final Set<InvoiceStatus> WRITEOFF_FROM = Set.of(InvoiceStatus.ISSUED, InvoiceStatus.PARTIALLY_PAID);
    private static final Set<InvoiceStatus> PAYABLE      = Set.of(InvoiceStatus.ISSUED, InvoiceStatus.PARTIALLY_PAID);

    private final InvoiceRepository invoiceRepository;
    private final InvoiceLineItemRepository lineItemRepository;
    private final InvoicePaymentRepository paymentRepository;
    private final InvoiceAuditLogRepository auditLogRepository;
    private final InvoiceIdGeneratorService idGeneratorService;
    private final InvoiceAuditService auditService;
    private final InvoiceMapper mapper;
    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final HospitalUserRepository hospitalUserRepository;
    private final RoleGuard roleGuard;

    @Value("${billing.tax-rate:0.00}")
    private BigDecimal taxRate;

    public InvoiceService(InvoiceRepository invoiceRepository,
                          InvoiceLineItemRepository lineItemRepository,
                          InvoicePaymentRepository paymentRepository,
                          InvoiceAuditLogRepository auditLogRepository,
                          InvoiceIdGeneratorService idGeneratorService,
                          InvoiceAuditService auditService,
                          InvoiceMapper mapper,
                          AppointmentRepository appointmentRepository,
                          PatientRepository patientRepository,
                          HospitalUserRepository hospitalUserRepository,
                          RoleGuard roleGuard) {
        this.invoiceRepository = invoiceRepository;
        this.lineItemRepository = lineItemRepository;
        this.paymentRepository = paymentRepository;
        this.auditLogRepository = auditLogRepository;
        this.idGeneratorService = idGeneratorService;
        this.auditService = auditService;
        this.mapper = mapper;
        this.appointmentRepository = appointmentRepository;
        this.patientRepository = patientRepository;
        this.hospitalUserRepository = hospitalUserRepository;
        this.roleGuard = roleGuard;
    }

    // ── US1: Generate Invoice ─────────────────────────────────────────────────

    @Transactional
    public InvoiceDetailResponse createInvoice(CreateInvoiceRequest request) {
        roleGuard.requireRoles("RECEPTIONIST", "ADMIN");
        AuthContext ctx = AuthContext.Holder.get();

        // Validate appointment exists
        Appointment appointment = appointmentRepository.findById(request.appointmentId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Appointment not found: " + request.appointmentId()));

        // Reject duplicate invoice
        if (invoiceRepository.existsByAppointmentId(request.appointmentId())) {
            throw new InvalidInvoiceTransitionException(
                    "An invoice already exists for appointment " + request.appointmentId());
        }

        // Compute monetary totals
        BigDecimal totalAmount = request.lineItems().stream()
                .map(li -> li.unitPrice()
                        .multiply(BigDecimal.valueOf(li.quantity()))
                        .setScale(2, RoundingMode.HALF_UP))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal discountPct = request.effectiveDiscountPercent();
        BigDecimal discountAmount = totalAmount
                .multiply(discountPct)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal netAmount = totalAmount.subtract(discountAmount).setScale(2, RoundingMode.HALF_UP);
        BigDecimal taxAmount = netAmount
                .multiply(taxRate)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal amountDue = netAmount.add(taxAmount).setScale(2, RoundingMode.HALF_UP);

        String invoiceId = idGeneratorService.generateInvoiceId();
        OffsetDateTime now = OffsetDateTime.now();

        Invoice invoice = Invoice.builder()
                .invoiceId(invoiceId)
                .appointmentId(appointment.getAppointmentId())
                .patientId(appointment.getPatientId())
                .doctorId(appointment.getDoctorId())
                .status(InvoiceStatus.DRAFT)
                .totalAmount(totalAmount)
                .discountPercent(discountPct)
                .discountAmount(discountAmount)
                .taxRate(taxRate)
                .taxAmount(taxAmount)
                .netAmount(netAmount)
                .amountDue(amountDue)
                .amountPaid(BigDecimal.ZERO)
                .notes(request.notes())
                .createdAt(now)
                .createdBy(ctx.getUsername())
                .updatedAt(now)
                .updatedBy(ctx.getUsername())
                .build();
        invoiceRepository.save(invoice);

        // Save line items
        List<InvoiceLineItem> items = request.lineItems().stream()
                .map(li -> InvoiceLineItem.builder()
                        .invoiceId(invoiceId)
                        .description(li.description())
                        .serviceCode(li.serviceCode())
                        .quantity(li.quantity())
                        .unitPrice(li.unitPrice())
                        .lineTotal(li.unitPrice()
                                .multiply(BigDecimal.valueOf(li.quantity()))
                                .setScale(2, RoundingMode.HALF_UP))
                        .build())
                .toList();
        lineItemRepository.saveAll(items);

        auditService.writeAuditLog(invoiceId, "CREATE", null, InvoiceStatus.DRAFT,
                ctx.getUsername(), null);

        return buildDetailResponse(invoice, items, List.of(), appointment);
    }

    // ── US2: List / Search Invoices ───────────────────────────────────────────

    @Transactional(readOnly = true)
    public PagedInvoiceSummaryResponse listInvoices(String patientId,
                                                     String appointmentId,
                                                     InvoiceStatus status,
                                                     LocalDate dateFrom,
                                                     LocalDate dateTo,
                                                     int page,
                                                     int size) {
        roleGuard.requireRoles("RECEPTIONIST", "ADMIN", "DOCTOR");
        size = Math.min(size, 100);
        AuthContext ctx = AuthContext.Holder.get();

        // DOCTOR scoping — restrict to their own appointments
        String effectiveDoctorId = "DOCTOR".equals(ctx.getRole()) ? ctx.getUserId() : null;

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Invoice> resultPage = invoiceRepository.findAll(
                InvoiceSpecifications.search(patientId, appointmentId, effectiveDoctorId, status, dateFrom, dateTo),
                pageable);

        List<InvoiceSummaryResponse> content = resultPage.getContent().stream()
                .map(inv -> mapper.toSummaryResponse(inv, resolvePatientName(inv.getPatientId())))
                .toList();

        return new PagedInvoiceSummaryResponse(content, resultPage.getNumber(), resultPage.getSize(),
                resultPage.getTotalElements(), resultPage.getTotalPages());
    }

    @Transactional(readOnly = true)
    public InvoiceDetailResponse getInvoice(String invoiceId) {
        roleGuard.requireRoles("RECEPTIONIST", "ADMIN", "DOCTOR");
        AuthContext ctx = AuthContext.Holder.get();

        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new InvoiceNotFoundException(invoiceId));

        // DOCTOR can only view invoices linked to their own appointments
        if ("DOCTOR".equals(ctx.getRole()) && !invoice.getDoctorId().equals(ctx.getUserId())) {
            throw new ForbiddenException("Access denied to invoice " + invoiceId);
        }

        List<InvoiceLineItem> items = lineItemRepository.findByInvoiceId(invoiceId);
        List<InvoicePayment> payments = paymentRepository.findByInvoiceId(invoiceId);
        Appointment appointment = appointmentRepository.findById(invoice.getAppointmentId()).orElse(null);

        return buildDetailResponse(invoice, items, payments, appointment);
    }

    @Transactional(readOnly = true)
    public PagedInvoiceSummaryResponse listInvoicesForPatient(String patientId, int page, int size) {
        roleGuard.requireRoles("RECEPTIONIST", "ADMIN", "DOCTOR");
        size = Math.min(size, 100);

        // Ensure patient exists
        if (!patientRepository.existsById(patientId)) {
            throw new ResourceNotFoundException("Patient not found: " + patientId);
        }

        AuthContext ctx = AuthContext.Holder.get();
        String effectiveDoctorId = "DOCTOR".equals(ctx.getRole()) ? ctx.getUserId() : null;

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Invoice> resultPage = invoiceRepository.findAll(
                InvoiceSpecifications.search(patientId, null, effectiveDoctorId, null, null, null),
                pageable);

        List<InvoiceSummaryResponse> content = resultPage.getContent().stream()
                .map(inv -> mapper.toSummaryResponse(inv, resolvePatientName(inv.getPatientId())))
                .toList();

        return new PagedInvoiceSummaryResponse(content, resultPage.getNumber(), resultPage.getSize(),
                resultPage.getTotalElements(), resultPage.getTotalPages());
    }

    // ── US3: Record Payment ───────────────────────────────────────────────────

    @Transactional
    public InvoiceDetailResponse recordPayment(String invoiceId, RecordPaymentRequest request) {
        roleGuard.requireRoles("RECEPTIONIST", "ADMIN");
        AuthContext ctx = AuthContext.Holder.get();

        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new InvoiceNotFoundException(invoiceId));

        if (!PAYABLE.contains(invoice.getStatus())) {
            throw new InvalidInvoiceTransitionException(
                    "Cannot record payment against a " + invoice.getStatus() + " invoice");
        }

        InvoiceStatus oldStatus = invoice.getStatus();

        // Save payment record
        InvoicePayment payment = InvoicePayment.builder()
                .invoiceId(invoiceId)
                .amount(request.amount())
                .paymentMethod(request.paymentMethod())
                .referenceNumber(request.referenceNumber())
                .notes(request.notes())
                .paidAt(OffsetDateTime.now())
                .recordedBy(ctx.getUsername())
                .build();
        paymentRepository.save(payment);

        // Update invoice balances
        BigDecimal newAmountPaid = invoice.getAmountPaid().add(request.amount()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal newAmountDue  = invoice.getAmountDue().subtract(request.amount()).setScale(2, RoundingMode.HALF_UP);
        InvoiceStatus newStatus  = newAmountDue.compareTo(BigDecimal.ZERO) > 0
                ? InvoiceStatus.PARTIALLY_PAID
                : InvoiceStatus.PAID;

        invoice.setAmountPaid(newAmountPaid);
        invoice.setAmountDue(newAmountDue);
        invoice.setStatus(newStatus);
        invoice.setUpdatedAt(OffsetDateTime.now());
        invoice.setUpdatedBy(ctx.getUsername());
        invoiceRepository.save(invoice);

        auditService.writeAuditLog(invoiceId, "PAYMENT", oldStatus, newStatus,
                ctx.getUsername(),
                "amount=" + request.amount() + " method=" + request.paymentMethod());

        List<InvoiceLineItem> items = lineItemRepository.findByInvoiceId(invoiceId);
        List<InvoicePayment> allPayments = paymentRepository.findByInvoiceId(invoiceId);
        Appointment appointment = appointmentRepository.findById(invoice.getAppointmentId()).orElse(null);

        return buildDetailResponse(invoice, items, allPayments, appointment);
    }

    // ── US4: Cancel / Write-off ───────────────────────────────────────────────

    @Transactional
    public InvoiceDetailResponse updateInvoiceStatus(String invoiceId, InvoiceStatusUpdateRequest request) {
        roleGuard.requireRoles("ADMIN");
        AuthContext ctx = AuthContext.Holder.get();

        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new InvoiceNotFoundException(invoiceId));

        InvoiceStatus oldStatus = invoice.getStatus();
        InvoiceStatus newStatus;

        switch (request.action()) {
            case CANCEL -> {
                if (!CANCEL_FROM.contains(oldStatus)) {
                    throw new InvalidInvoiceTransitionException(
                            "Cannot CANCEL an invoice in " + oldStatus + " status");
                }
                newStatus = InvoiceStatus.CANCELLED;
            }
            case WRITE_OFF -> {
                if (!WRITEOFF_FROM.contains(oldStatus)) {
                    throw new InvalidInvoiceTransitionException(
                            "Cannot WRITE_OFF an invoice in " + oldStatus + " status");
                }
                newStatus = InvoiceStatus.WRITTEN_OFF;
            }
            default -> throw new InvalidInvoiceTransitionException("Unknown action: " + request.action());
        }

        invoice.setStatus(newStatus);
        invoice.setCancelReason(request.reason());
        invoice.setUpdatedAt(OffsetDateTime.now());
        invoice.setUpdatedBy(ctx.getUsername());
        invoiceRepository.save(invoice);

        auditService.writeAuditLog(invoiceId, request.action().name(), oldStatus, newStatus,
                ctx.getUsername(), request.reason());

        List<InvoiceLineItem> items = lineItemRepository.findByInvoiceId(invoiceId);
        List<InvoicePayment> payments = paymentRepository.findByInvoiceId(invoiceId);
        Appointment appointment = appointmentRepository.findById(invoice.getAppointmentId()).orElse(null);

        return buildDetailResponse(invoice, items, payments, appointment);
    }

    // ── US5: Financial Report ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public FinancialReportResponse getFinancialReport(LocalDate dateFrom, LocalDate dateTo) {
        roleGuard.requireRoles("ADMIN");

        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new IllegalArgumentException("dateFrom must not be after dateTo");
        }

        LocalDate from = dateFrom != null ? dateFrom : LocalDate.of(1970, 1, 1);
        LocalDate to   = dateTo   != null ? dateTo   : LocalDate.now();

        List<Object[]> summaryRows = invoiceRepository.getFinancialSummary(from, to);
        Object[] summary = summaryRows.isEmpty() ? new Object[]{0, 0, 0, 0, 0, 0, 0} : summaryRows.get(0);
        BigDecimal totalInvoiced    = toBigDecimal(summary[0]);
        BigDecimal totalOutstanding = toBigDecimal(summary[1]);
        BigDecimal totalWrittenOff  = toBigDecimal(summary[2]);
        BigDecimal totalCancelled   = toBigDecimal(summary[3]);
        int invoiceCount = summary[4] == null ? 0 : ((Number) summary[4]).intValue();
        int paidCount    = summary[5] == null ? 0 : ((Number) summary[5]).intValue();
        int partialCount = summary[6] == null ? 0 : ((Number) summary[6]).intValue();

        BigDecimal totalCollected = paymentRepository.sumCollectedForDateRange(from, to);
        if (totalCollected == null) totalCollected = BigDecimal.ZERO;

        Long overdueRaw = invoiceRepository.countOverdue(from, to);
        int overdueCount = overdueRaw == null ? 0 : overdueRaw.intValue();

        // byPaymentMethod
        List<Object[]> methodRows = paymentRepository.sumByPaymentMethodForDateRange(from, to);
        Map<String, BigDecimal> byPaymentMethod = new LinkedHashMap<>();
        for (PaymentMethod m : PaymentMethod.values()) {
            byPaymentMethod.put(m.name(), BigDecimal.ZERO);
        }
        for (Object[] row : methodRows) {
            String method = (String) row[0];
            BigDecimal amount = toBigDecimal(row[1]);
            byPaymentMethod.put(method, amount);
        }

        return new FinancialReportResponse(
                dateFrom, dateTo,
                totalInvoiced, totalCollected, totalOutstanding,
                totalWrittenOff, totalCancelled,
                invoiceCount, paidCount, partialCount, overdueCount,
                byPaymentMethod
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private InvoiceDetailResponse buildDetailResponse(Invoice invoice,
                                                       List<InvoiceLineItem> items,
                                                       List<InvoicePayment> payments,
                                                       Appointment appointment) {
        String patientName = resolvePatientName(invoice.getPatientId());
        String doctorName  = resolveDoctorName(invoice.getDoctorId());
        String apptDate    = appointment != null ? appointment.getAppointmentDate().toString() : null;

        List<LineItemResponse> lineItemResponses = items.stream()
                .map(mapper::toLineItemResponse)
                .toList();
        List<PaymentResponse> paymentResponses = payments.stream()
                .map(mapper::toPaymentResponse)
                .toList();

        return mapper.toDetailResponse(invoice, patientName, doctorName, apptDate,
                lineItemResponses, paymentResponses);
    }

    private String resolvePatientName(String patientId) {
        return patientRepository.findById(patientId)
                .map(p -> p.getFirstName() + " " + p.getLastName())
                .orElse(patientId);
    }

    private String resolveDoctorName(String doctorId) {
        return hospitalUserRepository.findById(doctorId)
                .map(HospitalUser::getUsername)
                .orElse(doctorId);
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal bd) return bd.setScale(2, RoundingMode.HALF_UP);
        return new BigDecimal(value.toString()).setScale(2, RoundingMode.HALF_UP);
    }
}

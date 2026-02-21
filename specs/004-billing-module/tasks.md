# Tasks: Billing & Invoicing Module

**Input**: Design documents from `/specs/004-billing-module/`
**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, contracts/invoices.yaml ✅, quickstart.md ✅

**Package root**: `backend/src/main/java/com/ainexus/hospital/patient/`
**Test root**: `backend/src/test/java/com/ainexus/hospital/patient/`
**Migrations**: `backend/src/main/resources/db/migration/`

---

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel with other [P] tasks in the same phase
- **[Story]**: Which user story this task belongs to (US1–US5)
- All monetary fields use `BigDecimal`; all ID generation uses `PESSIMISTIC_WRITE` lock

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Flyway migrations, enums, and Jackson config — prerequisites for all entities

- [X] T001 Add `spring.jackson.serialization.write-big-decimal-as-plain: true` and `billing.tax-rate: 0.00` to `backend/src/main/resources/application.yml` and `backend/src/main/resources/application-test.yml`
- [X] T002 Create Flyway migration `V12__create_invoices.sql` — `invoices` table (VARCHAR(16) PK, appointmentId UNIQUE FK, patientId, doctorId, status VARCHAR(20) DEFAULT 'DRAFT', all NUMERIC(12,2) monetary fields, cancel_reason TEXT, version INT DEFAULT 0, audit timestamps) and `invoice_id_sequences` table (year INT PK, last_sequence INT DEFAULT 0) in `backend/src/main/resources/db/migration/`
- [X] T003 [P] Create Flyway migration `V13__create_invoice_line_items.sql` — `invoice_line_items` table (id BIGINT GENERATED ALWAYS, invoice_id FK, service_code VARCHAR(20), description TEXT NOT NULL, quantity INT CHECK > 0, unit_price NUMERIC(10,2) CHECK > 0, line_total NUMERIC(12,2)) in `backend/src/main/resources/db/migration/`
- [X] T004 [P] Create Flyway migration `V14__create_invoice_payments.sql` — `invoice_payments` table (id BIGINT GENERATED ALWAYS, invoice_id FK, amount NUMERIC(12,2) CHECK > 0, payment_method VARCHAR(20), reference_number VARCHAR(100), notes TEXT, paid_at TIMESTAMPTZ DEFAULT NOW(), recorded_by VARCHAR(100)) in `backend/src/main/resources/db/migration/`
- [X] T005 [P] Create Flyway migration `V15__create_invoice_audit_log.sql` — `invoice_audit_log` table (id BIGINT GENERATED ALWAYS, invoice_id VARCHAR(16) NO FK, action VARCHAR(30), from_status VARCHAR(20), to_status VARCHAR(20) NOT NULL, performed_by VARCHAR(100), performed_at TIMESTAMPTZ DEFAULT NOW(), details TEXT) in `backend/src/main/resources/db/migration/`
- [X] T006 [P] Create Flyway migration `V16__create_billing_indexes.sql` — indexes on invoices(patient_id), invoices(appointment_id), invoices(status), invoices(created_at DESC), invoices(doctor_id), invoice_line_items(invoice_id), invoice_payments(invoice_id), invoice_audit_log(invoice_id), invoice_audit_log(performed_at DESC) in `backend/src/main/resources/db/migration/`
- [X] T007 [P] Create enum `InvoiceStatus.java` (DRAFT, ISSUED, PARTIALLY_PAID, PAID, CANCELLED, WRITTEN_OFF) in `backend/src/main/java/com/ainexus/hospital/patient/entity/enums/`
- [X] T008 [P] Create enum `PaymentMethod.java` (CASH, CARD, INSURANCE, BANK_TRANSFER, CHEQUE) in `backend/src/main/java/com/ainexus/hospital/patient/entity/enums/`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Entities, repositories, ID generator, and audit service — shared by all user stories

**⚠️ CRITICAL**: No user story implementation can begin until this phase is complete.

- [X] T009 Create `Invoice.java` JPA entity with all fields from data-model.md (`@Id invoiceId VARCHAR(16)`, `@Enumerated(EnumType.STRING) status`, all BigDecimal monetary columns with `@Column(precision,scale)`, `@Version Integer version`, `OffsetDateTime createdAt/updatedAt`) in `backend/src/main/java/com/ainexus/hospital/patient/entity/`
- [X] T010 [P] Create `InvoiceLineItem.java` JPA entity (`@Id @GeneratedValue(IDENTITY) Long id`, invoiceId VARCHAR(16) FK column, serviceCode, description, quantity, unitPrice, lineTotal) in `backend/src/main/java/com/ainexus/hospital/patient/entity/`
- [X] T011 [P] Create `InvoicePayment.java` JPA entity (`@Id @GeneratedValue(IDENTITY) Long id`, invoiceId FK column, amount, `@Enumerated paymentMethod`, referenceNumber, notes, paidAt OffsetDateTime, recordedBy) in `backend/src/main/java/com/ainexus/hospital/patient/entity/`
- [X] T012 [P] Create `InvoiceAuditLog.java` JPA entity (`@Id @GeneratedValue(IDENTITY) Long id`, invoiceId, action, fromStatus, toStatus, performedBy, performedAt OffsetDateTime, details — no `@Version`) in `backend/src/main/java/com/ainexus/hospital/patient/entity/`
- [X] T013 [P] Create `InvoiceIdSequence.java` JPA entity (`@Id Integer year`, `Integer lastSequence`) mirroring `PatientIdSequence` in `backend/src/main/java/com/ainexus/hospital/patient/entity/`
- [X] T014 Create `InvoiceRepository.java` extending `JpaRepository<Invoice, String>` and `JpaSpecificationExecutor<Invoice>` with methods: `findByAppointmentId(String)`, `existsByAppointmentId(String)` in `backend/src/main/java/com/ainexus/hospital/patient/repository/`
- [X] T015 [P] Create `InvoiceLineItemRepository.java` extending `JpaRepository<InvoiceLineItem, Long>` with `findByInvoiceId(String)` in `backend/src/main/java/com/ainexus/hospital/patient/repository/`
- [X] T016 [P] Create `InvoicePaymentRepository.java` extending `JpaRepository<InvoicePayment, Long>` with `findByInvoiceId(String)` in `backend/src/main/java/com/ainexus/hospital/patient/repository/`
- [X] T017 [P] Create `InvoiceAuditLogRepository.java` extending `JpaRepository<InvoiceAuditLog, Long>` with `findByInvoiceId(String)` in `backend/src/main/java/com/ainexus/hospital/patient/repository/`
- [X] T018 [P] Create `InvoiceIdSequenceRepository.java` extending `JpaRepository<InvoiceIdSequence, Integer>` with `@Lock(PESSIMISTIC_WRITE) @Query("SELECT s FROM InvoiceIdSequence s WHERE s.year = :year") Optional<InvoiceIdSequence> findByYearForUpdate(@Param("year") int year)` in `backend/src/main/java/com/ainexus/hospital/patient/repository/`
- [X] T019 Create `InvoiceIdGeneratorService.java` with `@Transactional generateInvoiceId()` method that locks the row for the current year, increments `lastSequence`, and returns `"INV" + year + String.format("%05d", seq)` — mirroring `AppointmentIdGeneratorService` exactly in `backend/src/main/java/com/ainexus/hospital/patient/service/`
- [X] T020 Create `InvoiceAuditService.java` with `@Transactional(propagation = Propagation.MANDATORY) void log(String invoiceId, String action, InvoiceStatus from, InvoiceStatus to, String performedBy, String details)` that saves an `InvoiceAuditLog` entry in `backend/src/main/java/com/ainexus/hospital/patient/service/`
- [X] T021 Create `InvoiceSpecification.java` with static factory methods: `patientId(String)`, `appointmentId(String)`, `status(InvoiceStatus)`, `doctorId(String)`, `createdBetween(LocalDate from, LocalDate to)` — each returns a `Specification<Invoice>` in `backend/src/main/java/com/ainexus/hospital/patient/service/`
- [X] T022 Add custom exceptions `InvoiceNotFoundException.java` and `InvalidInvoiceTransitionException.java` (extending `RuntimeException`) to `backend/src/main/java/com/ainexus/hospital/patient/exception/`; add `@ExceptionHandler` entries for both in `GlobalExceptionHandler.java` (404 and 409 respectively)
- [X] T023 Create `InvoiceMapper.java` MapStruct interface with: `Invoice toEntity(CreateInvoiceRequest)`, `InvoiceDetailResponse toDetailResponse(Invoice)`, `InvoiceSummaryResponse toSummaryResponse(Invoice)` — `lineItems` and `payments` mapped separately (not via `@OneToMany`) in `backend/src/main/java/com/ainexus/hospital/patient/mapper/`
- [X] T024 Write unit tests for `InvoiceIdGeneratorService` verifying: first invoice of year gets `INV{year}00001`, sequential calls increment, concurrent-safe via lock in `backend/src/test/java/com/ainexus/hospital/patient/unit/service/InvoiceIdGeneratorServiceTest.java`

**Checkpoint**: Foundation ready — all US phases can now proceed.

---

## Phase 3: US1 — Generate Invoice (Priority: P1) 🎯 MVP

**Goal**: RECEPTIONIST/ADMIN can POST an invoice; system computes all monetary totals and returns a DRAFT invoice with a unique INV-prefixed ID.

**Independent Test** (from quickstart.md Scenario 1): POST invoice with 2 line items + 10% discount → HTTP 201, `totalAmount=500.00`, `discountAmount=50.00`, `netAmount=450.00`, `amountDue=450.00`, `status=DRAFT`.

- [X] T025 [US1] Create `CreateInvoiceRequest.java` DTO (`@NotBlank appointmentId`, `@NotEmpty @Valid List<LineItemRequest> lineItems`, `@DecimalMin("0.00") @DecimalMax("100.00") BigDecimal discountPercent`, `String notes`) in `backend/src/main/java/com/ainexus/hospital/patient/dto/request/`
- [X] T026 [P] [US1] Create `LineItemRequest.java` DTO (`@NotBlank String description`, `@Min(1) Integer quantity`, `@DecimalMin(exclusive=true, value="0.0") BigDecimal unitPrice`, `String serviceCode`) in `backend/src/main/java/com/ainexus/hospital/patient/dto/request/`
- [X] T027 [P] [US1] Create `InvoiceDetailResponse.java` DTO (all Invoice fields + `String patientName`, `String doctorName`, `String appointmentDate`, `List<LineItemResponse> lineItems`, `List<PaymentResponse> payments`) in `backend/src/main/java/com/ainexus/hospital/patient/dto/response/`
- [X] T028 [P] [US1] Create `LineItemResponse.java` DTO (`Long id`, `String serviceCode`, `String description`, `Integer quantity`, `BigDecimal unitPrice`, `BigDecimal lineTotal`) in `backend/src/main/java/com/ainexus/hospital/patient/dto/response/`
- [X] T029 [US1] Implement `InvoiceService.createInvoice(CreateInvoiceRequest, AuthContext)` in `backend/src/main/java/com/ainexus/hospital/patient/service/InvoiceService.java`:
  - `roleGuard.requireAnyRole("RECEPTIONIST","ADMIN")`
  - Verify appointment exists (query `appointments` table by `appointmentId`) — throw `AppointmentNotFoundException` (404) if not found
  - Check `invoiceRepository.existsByAppointmentId(appointmentId)` — throw `InvalidInvoiceTransitionException` (409) if duplicate
  - Compute `totalAmount = sum(qty × unitPrice).setScale(2, HALF_UP)` for each line item
  - Compute `discountAmount`, `netAmount`, `taxAmount` (from `@Value("${billing.tax-rate:0.00}")`), `amountDue` all with `setScale(2, HALF_UP)`
  - Generate `invoiceId` via `invoiceIdGeneratorService.generateInvoiceId()`
  - Save `Invoice`, save all `InvoiceLineItem` records
  - Call `invoiceAuditService.log(invoiceId, "CREATE", null, DRAFT, username, null)`
  - Return assembled `InvoiceDetailResponse`
- [X] T030 [US1] Create `InvoiceController.java` with `@PostMapping("/api/v1/invoices") @ResponseStatus(CREATED)` calling `invoiceService.createInvoice()` in `backend/src/main/java/com/ainexus/hospital/patient/controller/`
- [X] T031 [US1] Write unit tests for `InvoiceService.createInvoice()` covering: correct monetary arithmetic with 10% discount, duplicate appointment rejection (409), missing appointment rejection (404), NURSE role rejection (403), DOCTOR role rejection (403) in `backend/src/test/java/com/ainexus/hospital/patient/unit/service/InvoiceServiceTest.java`
- [X] T032 [US1] Write integration test `InvoiceLifecycleIT.java` covering: happy-path 2-line-item invoice (verify all 5 monetary fields), duplicate invoice 409, non-existent appointment 404, NURSE denied 403 in `backend/src/test/java/com/ainexus/hospital/patient/integration/InvoiceLifecycleIT.java`

---

## Phase 4: US2 — View & Search Invoices (Priority: P1)

**Goal**: RECEPTIONIST/ADMIN can list/filter all invoices; DOCTOR sees only their own patients' invoices; anyone can fetch full detail including line items and payment history.

**Independent Test** (from quickstart.md Scenarios 4–7): Seed 3 invoices; filter by `status=DRAFT` returns only DRAFT; DOCTOR token returns only that doctor's invoices; GET detail includes `lineItems` and `payments` arrays.

- [X] T033 [US2] Create `InvoiceSummaryResponse.java` DTO (`invoiceId`, `appointmentId`, `patientId`, `patientName`, `doctorId`, `status`, `totalAmount`, `amountDue`, `amountPaid`, `createdAt`) in `backend/src/main/java/com/ainexus/hospital/patient/dto/response/`
- [X] T034 [P] [US2] Create `PagedInvoiceSummaryResponse.java` DTO wrapping `List<InvoiceSummaryResponse>` with `page`, `size`, `totalElements`, `totalPages` in `backend/src/main/java/com/ainexus/hospital/patient/dto/response/`
- [X] T035 [US2] Implement `InvoiceService.listInvoices(patientId, appointmentId, status, dateFrom, dateTo, pageable, AuthContext)` in `InvoiceService.java`:
  - `roleGuard.requireAnyRole("RECEPTIONIST","ADMIN","DOCTOR")`
  - Build `Specification<Invoice>` from non-null filter params using `InvoiceSpecification`
  - If role=DOCTOR: AND with `InvoiceSpecification.doctorId(ctx.getUsername())`
  - `invoiceRepository.findAll(spec, pageable)` sorted by `createdAt DESC`
  - For each Invoice, look up patient name from `PatientRepository` and map to `InvoiceSummaryResponse`
  - Return `PagedInvoiceSummaryResponse`
- [X] T036 [US2] Implement `InvoiceService.getInvoice(invoiceId, AuthContext)` in `InvoiceService.java`:
  - `roleGuard.requireAnyRole("RECEPTIONIST","ADMIN","DOCTOR")`
  - Load `Invoice` or throw `InvoiceNotFoundException`
  - If role=DOCTOR: verify `invoice.getDoctorId().equals(ctx.getUsername())` or throw 403
  - Load `lineItems` via `invoiceLineItemRepository.findByInvoiceId(invoiceId)`
  - Load `payments` via `invoicePaymentRepository.findByInvoiceId(invoiceId)`
  - Look up patient name from `PatientRepository`, doctor name from `HospitalUserRepository`
  - Return assembled `InvoiceDetailResponse`
- [X] T037 [US2] Add endpoints to `InvoiceController.java`: `@GetMapping("/api/v1/invoices")` → `listInvoices()` with `@RequestParam` filters and `Pageable`; `@GetMapping("/api/v1/invoices/{invoiceId}")` → `getInvoice()`
- [X] T038 [US2] Create `PatientInvoiceController.java` with `@GetMapping("/api/v1/patients/{patientId}/invoices")` that delegates to `InvoiceService.listInvoicesForPatient(patientId, pageable, AuthContext)` (adds mandatory `patientId` filter, verifies patient exists via `PatientRepository`) in `backend/src/main/java/com/ainexus/hospital/patient/controller/`
- [X] T039 [P] [US2] Create `PaymentResponse.java` DTO (`Long id`, `BigDecimal amount`, `PaymentMethod paymentMethod`, `String referenceNumber`, `String notes`, `OffsetDateTime paidAt`, `String recordedBy`) in `backend/src/main/java/com/ainexus/hospital/patient/dto/response/`
- [X] T040 [US2] Write integration test `InvoiceSearchIT.java` covering: list all (RECEPTIONIST), filter by status, filter by patientId, filter by dateFrom/dateTo, DOCTOR scope (only own), NURSE denied 403, GET detail includes lineItems + payments, patient not found 404 in `backend/src/test/java/com/ainexus/hospital/patient/integration/InvoiceSearchIT.java`

---

## Phase 5: US3 — Record Payment (Priority: P2)

**Goal**: RECEPTIONIST/ADMIN records a payment; `amountPaid` and `amountDue` update atomically; status auto-transitions to PARTIALLY_PAID or PAID; overpayments accepted.

**Independent Test** (from quickstart.md Scenarios 8–11): ISSUED invoice 450.00 → partial 100.00 → status=PARTIALLY_PAID, amountDue=350.00; second payment 350.00 → status=PAID, amountDue=0.00; overpayment → amountDue negative, status=PAID; payment on CANCELLED → 409.

- [X] T041 [US3] Create `RecordPaymentRequest.java` DTO (`@DecimalMin(exclusive=true) BigDecimal amount`, `@NotNull PaymentMethod paymentMethod`, `String referenceNumber`, `String notes`) in `backend/src/main/java/com/ainexus/hospital/patient/dto/request/`
- [X] T042 [US3] Implement `InvoiceService.recordPayment(invoiceId, RecordPaymentRequest, AuthContext)` in `InvoiceService.java`:
  - `roleGuard.requireAnyRole("RECEPTIONIST","ADMIN")`
  - Load invoice or throw `InvoiceNotFoundException`
  - Validate `invoice.getStatus()` is ISSUED or PARTIALLY_PAID — throw `InvalidInvoiceTransitionException` (409) otherwise
  - Save `InvoicePayment` record with `paidAt=now()`, `recordedBy=ctx.getUsername()`
  - `amountPaid = amountPaid.add(payment.getAmount()).setScale(2, HALF_UP)`
  - `amountDue = amountDue.subtract(payment.getAmount()).setScale(2, HALF_UP)`
  - New status: `amountDue.compareTo(ZERO) > 0 ? PARTIALLY_PAID : PAID`
  - Update `invoice.status`, `updatedAt`, `updatedBy`
  - Call `invoiceAuditService.log(invoiceId, "PAYMENT", oldStatus, newStatus, username, "amount=" + payment.getAmount() + " method=" + payment.getPaymentMethod())`
  - Return `InvoiceDetailResponse` with updated invoice + all payments
- [X] T043 [US3] Add `@PostMapping("/api/v1/invoices/{invoiceId}/payments")` endpoint to `InvoiceController.java` calling `invoiceService.recordPayment()`
- [X] T044 [US3] Write integration test `InvoicePaymentIT.java` covering: ISSUED → partial payment → PARTIALLY_PAID, PARTIALLY_PAID → full payment → PAID, overpayment (amountDue goes negative → PAID), payment on DRAFT rejected 409, payment on CANCELLED rejected 409, payment on PAID rejected 409, NURSE denied 403, audit log entry created per payment in `backend/src/test/java/com/ainexus/hospital/patient/integration/InvoicePaymentIT.java`

---

## Phase 6: US4 — Cancel / Write-off Invoice (Priority: P2)

**Goal**: ADMIN can cancel (from DRAFT or ISSUED) or write off (from ISSUED or PARTIALLY_PAID) an invoice with a mandatory reason; terminal states cannot be further modified.

**Independent Test** (from quickstart.md Scenarios 12–14): Cancel DRAFT → CANCELLED, `cancelReason` set; write-off PARTIALLY_PAID → WRITTEN_OFF; cancel PAID → 409; RECEPTIONIST → 403.

- [X] T045 [US4] Create `InvoiceStatusUpdateRequest.java` DTO (`@NotNull InvoiceAction action` where `InvoiceAction` enum = CANCEL | WRITE_OFF, `@NotBlank String reason`) in `backend/src/main/java/com/ainexus/hospital/patient/dto/request/`; create `InvoiceAction.java` enum in `backend/src/main/java/com/ainexus/hospital/patient/entity/enums/`
- [X] T046 [US4] Implement `InvoiceService.updateInvoiceStatus(invoiceId, InvoiceStatusUpdateRequest, AuthContext)` in `InvoiceService.java`:
  - `roleGuard.requireRole("ADMIN")`
  - Load invoice or throw `InvoiceNotFoundException`
  - Validate transition: CANCEL allowed from DRAFT or ISSUED; WRITE_OFF allowed from ISSUED or PARTIALLY_PAID — throw `InvalidInvoiceTransitionException` (409) for invalid
  - Set `invoice.setStatus(target)`, `invoice.setCancelReason(request.getReason())`, `updatedAt`, `updatedBy`
  - Call `invoiceAuditService.log(invoiceId, action.name(), oldStatus, newStatus, username, reason)`
  - Return updated `InvoiceDetailResponse`
- [X] T047 [US4] Add `@PatchMapping("/api/v1/invoices/{invoiceId}/status")` endpoint to `InvoiceController.java` calling `invoiceService.updateInvoiceStatus()`; configure `HttpComponentsClientHttpRequestFactory` for PATCH in test `@BeforeEach` (mirrors existing pattern from Module 1)
- [X] T048 [US4] Write integration test `InvoiceStatusIT.java` covering: cancel DRAFT → CANCELLED with reason, cancel ISSUED → CANCELLED, cancel PAID → 409, write-off ISSUED → WRITTEN_OFF, write-off PARTIALLY_PAID → WRITTEN_OFF, write-off PAID → 409, write-off CANCELLED → 409, RECEPTIONIST cancel attempt → 403, DOCTOR cancel attempt → 403, audit log entry created in `backend/src/test/java/com/ainexus/hospital/patient/integration/InvoiceStatusIT.java`

---

## Phase 7: US5 — Financial Summary Report (Priority: P2)

**Goal**: ADMIN retrieves aggregated financial metrics for a date range; overdue count includes ISSUED/PARTIALLY_PAID invoices where appointment date < today; empty range returns zeros.

**Independent Test** (from quickstart.md Scenarios 15–17): Seed known invoices → verify `totalInvoiced`, `totalCollected`, `paidCount`, `byPaymentMethod`; empty range → all zeros; RECEPTIONIST → 403.

- [X] T049 [US5] Create `FinancialReportResponse.java` DTO (`LocalDate dateFrom/dateTo`, `BigDecimal totalInvoiced/totalCollected/totalOutstanding/totalWrittenOff/totalCancelled`, `Integer invoiceCount/paidCount/partialCount/overdueCount`, `Map<String, BigDecimal> byPaymentMethod`) in `backend/src/main/java/com/ainexus/hospital/patient/dto/response/`
- [X] T050 [US5] Add financial report query to `InvoiceRepository.java`: native `@Query` aggregating `total_invoiced` (SUM of `total_amount`), `total_outstanding` (SUM of `amount_due` WHERE status IN ('ISSUED','PARTIALLY_PAID')), `total_written_off`, `total_cancelled`, `invoice_count`, `paid_count`, `partial_count` — filtered by `created_at::date BETWEEN :dateFrom AND :dateTo`; use Spring Data Projection or plain `Object[]`; add separate query for `byPaymentMethod` via `InvoicePaymentRepository`
- [X] T051 [US5] Add overdue count query to `InvoiceRepository.java`: count invoices WHERE `status IN ('ISSUED','PARTIALLY_PAID')` AND linked appointment's date < today (join via `appointment_id` against `appointments` table using native SQL)
- [X] T052 [US5] Implement `InvoiceService.getFinancialReport(LocalDate dateFrom, LocalDate dateTo, AuthContext)` in `InvoiceService.java`:
  - `roleGuard.requireRole("ADMIN")`
  - Validate `dateFrom <= dateTo` — throw `IllegalArgumentException` (400) if not
  - Execute aggregation queries; if no data found return all-zero `FinancialReportResponse`
  - Fetch `byPaymentMethod` by summing `invoice_payments.amount` grouped by `payment_method` for payments whose invoice falls in the date range
  - Populate all zero-defaults for payment methods not present in results
  - Return `FinancialReportResponse`
- [X] T053 [US5] Create `FinancialReportController.java` with `@GetMapping("/api/v1/reports/financial")` accepting `@RequestParam @DateTimeFormat(iso=DATE) LocalDate dateFrom/dateTo` calling `invoiceService.getFinancialReport()` in `backend/src/main/java/com/ainexus/hospital/patient/controller/`
- [X] T054 [US5] Write integration test `FinancialReportIT.java` covering: seeded invoices → verify all aggregate fields, `byPaymentMethod` breakdown correct, overdue count correct, empty date range → all zeros, dateFrom > dateTo → 400, RECEPTIONIST → 403, DOCTOR → 403 in `backend/src/test/java/com/ainexus/hospital/patient/integration/FinancialReportIT.java`

---

## Phase 8: Polish & Cross-Cutting

**Purpose**: RBAC integration test across all endpoints, smoke-test the full lifecycle end-to-end.

- [X] T055 Write `InvoiceRbacIT.java` covering the full role matrix (RECEPTIONIST/DOCTOR/NURSE/ADMIN) for all 7 endpoints: `POST /invoices`, `GET /invoices`, `GET /invoices/{id}`, `POST /invoices/{id}/payments`, `PATCH /invoices/{id}/status`, `GET /patients/{id}/invoices`, `GET /reports/financial` — verifying 200/201 for allowed roles and 403 for denied roles in `backend/src/test/java/com/ainexus/hospital/patient/integration/InvoiceRbacIT.java`
- [X] T056 Write end-to-end lifecycle integration test in `InvoiceLifecycleIT.java` (add to existing class): seed appointment → create invoice (DRAFT) → issue (no separate endpoint; seed ISSUED directly or extend status endpoint to support ISSUE action per status machine) → record partial payment → record full payment → verify PAID status → ADMIN write-off a different invoice → verify WRITTEN_OFF + audit log has 4 entries
- [X] T057 [P] Add `@Tag("billing")` and `@DisplayName` annotations to all billing IT classes for test reporting clarity; verify all 5 `FinancialReportResponse` monetary fields return plain decimal notation (no scientific notation) by asserting JSON response body with `BigDecimal` parsing
- [X] T058 [P] Run `mvn verify -Pfailsafe` and confirm all existing 108 tests still pass alongside new billing tests; fix any Flyway checksum issues if migrations are re-ordered

---

## Dependencies

```
Phase 1 (Migrations + Enums)
    ↓
Phase 2 (Entities + Repositories + ID Generator + Audit Service)
    ↓
Phase 3 (US1 — Create Invoice) ←── MVP: stop here for first delivery
    ↓                    ↓
Phase 4 (US2 — Search)   Phase 5 (US3 — Payment)
                              ↓
                         Phase 6 (US4 — Cancel/Write-off)
                         Phase 7 (US5 — Report) ← needs US1 + US3 data
    ↓
Phase 8 (Polish — RBAC matrix + lifecycle IT)
```

US2 (Search) can begin in parallel with US3 (Payment) once Phase 3 is done.
US5 (Report) depends on US1 + US3 for meaningful test data but can be coded independently.

---

## Parallel Execution Examples

**Within Phase 2** (run together):
- T010 (`InvoiceLineItem.java`) ∥ T011 (`InvoicePayment.java`) ∥ T012 (`InvoiceAuditLog.java`) ∥ T013 (`InvoiceIdSequence.java`)
- T015 (`LineItemRepository`) ∥ T016 (`PaymentRepository`) ∥ T017 (`AuditLogRepository`) ∥ T018 (`IdSequenceRepository`)

**Within Phase 3** (run together after T025):
- T026 (`LineItemRequest.java`) ∥ T027 (`InvoiceDetailResponse.java`) ∥ T028 (`LineItemResponse.java`)

**Within Phase 4** (run together after T033):
- T034 (`PagedInvoiceSummaryResponse.java`) ∥ T039 (`PaymentResponse.java`)

---

## Implementation Strategy

| Milestone | Phases | Deliverable |
|-----------|--------|-------------|
| **MVP** | 1–3 | Invoice creation working end-to-end; receptionist can generate a DRAFT invoice |
| **Read** | 4 | Invoice list, filter, and detail view; DOCTOR scoping working |
| **Payments** | 5 | Payment recording with atomic balance updates and auto-status transitions |
| **Admin** | 6–7 | Cancel/write-off + financial report; full ADMIN functionality |
| **Complete** | 8 | RBAC matrix verified; all tests green |

**Total tasks**: T001–T058 = **58 tasks**

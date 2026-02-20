# Research: 004-billing-module

**Date**: 2026-02-20
**Branch**: `004-billing-module`

---

## Decision 1: Monetary Precision — BigDecimal + NUMERIC(12,2)

**Decision**: Use `java.math.BigDecimal` in all Java entity/service/DTO layers; map to PostgreSQL `NUMERIC(12,2)` via `@Column(precision=12, scale=2)`.

**Rationale**:
- `double`/`float` are forbidden for financial calculations — IEEE 754 floating-point introduces rounding errors (e.g., 0.1 + 0.2 ≠ 0.3)
- `BigDecimal` with `RoundingMode.HALF_UP` at scale 2 matches standard accounting rounding
- Hibernate 6 (included with Spring Boot 3.2.x) auto-maps `BigDecimal` ↔ `NUMERIC` — no custom type registration needed
- `@Column(precision=12, scale=2)` is portable and generates correct DDL; `columnDefinition="NUMERIC(12,2)"` is PostgreSQL-specific

**Arithmetic pattern**:
```java
BigDecimal result = value.multiply(rate).setScale(2, RoundingMode.HALF_UP);
```

**Jackson JSON serialization**: Enable `write-big-decimal-as-plain: true` in `application.yml` to prevent scientific notation in JSON responses. No per-field annotation needed.

**Alternatives considered**:
- `long` cents (store as integer pennies) — rejected: requires custom conversion logic everywhere, error-prone with discounts/tax
- `double` — rejected: floating-point precision errors unacceptable in financial records

---

## Decision 2: Invoice ID Generation — Mirror AppointmentIdGeneratorService

**Decision**: Create `InvoiceIdGeneratorService` mirroring the existing `AppointmentIdGeneratorService` pattern exactly.

**Format**: `INV` + YYYY + 5-digit zero-padded seq → `INV2026000001`
- 5-digit padding: handles up to 99,999 invoices/year; expands naturally past that
- Annual reset: sequence restarts at 1 each calendar year

**Locking strategy**: `@Lock(LockModeType.PESSIMISTIC_WRITE)` on `findByYearForUpdate()` — PostgreSQL `SELECT FOR UPDATE`. Guarantees no duplicate IDs under concurrent load. This is proven in production by modules 1 and 3.

**Pattern confirmed in codebase**:
- `PatientIdGeneratorService` → `P` + YYYY + 3-digit seq
- `AppointmentIdGeneratorService` → `APT` + YYYY + 4-digit seq
- Both use `InvoiceIdSequenceRepository.findByYearForUpdate()` with `PESSIMISTIC_WRITE`

**Alternatives considered**:
- PostgreSQL `SERIAL`/`SEQUENCE` — rejected: non-portable, harder to format with prefix+year
- UUID — rejected: not human-readable; reception staff needs to reference IDs verbally

---

## Decision 3: Entity Relationship Strategy — FK Columns, No JPA Navigation

**Decision**: Use plain foreign key columns in child entities (`invoiceId` in `InvoiceLineItem` and `InvoicePayment`); fetch related records via repository queries. No `@OneToMany` / `@ManyToOne` navigation properties.

**Rationale**: This is the established codebase pattern — `ClinicalNotes` uses `appointmentId` as FK without JPA navigation. `Patient` and `Appointment` entities have zero JPA relationship annotations. This keeps entities simple, avoids N+1 query issues, and matches the existing repository-composition pattern.

**Implementation**: `InvoiceService` fetches line items and payments via `InvoiceLineItemRepository.findByInvoiceId()` and `InvoicePaymentRepository.findByInvoiceId()`, then assembles the response DTO.

**Alternatives considered**:
- `@OneToMany(cascade=ALL, fetch=LAZY)` — rejected: breaks with existing patterns, adds Hibernate complexity, risk of accidental cascade deletes

---

## Decision 4: Flyway Migration Versioning — Start at V12

**Decision**: First billing migration is `V12__create_invoices.sql`. Full sequence:
- V12 — `invoices` table + `invoice_id_sequences`
- V13 — `invoice_line_items` table
- V14 — `invoice_payments` table
- V15 — `invoice_audit_log` table
- V16 — indexes

**Confirmed**: V11 (`V11__create_clinical_notes.sql`) is the highest existing migration. All V1–V11 must remain untouched.

---

## Decision 5: Audit Service — Mirror AppointmentAuditService Pattern

**Decision**: Create `InvoiceAuditService` with `@Transactional(propagation = Propagation.MANDATORY)`, called within `InvoiceService` transactional methods.

**Pattern**: Same as `AppointmentAuditService` — captures invoiceId, action, fromStatus, toStatus, performedBy, performedAt, details.

**Key constraint**: `Propagation.MANDATORY` ensures audit writes always occur within an active transaction — if the outer transaction rolls back, the audit entry is also rolled back (no orphan audit entries for failed operations).

---

## Decision 6: Jackson BigDecimal Config — application.yml Flag

**Decision**: Add `spring.jackson.serialization.write-big-decimal-as-plain: true` to `application.yml` and `application-test.yml`.

**Rationale**: Global flag is cleaner than per-field `@JsonSerialize` on every monetary DTO field. No new beans needed — Spring Boot's `JacksonAutoConfiguration` picks up the YAML setting automatically.

---

## Decision 7: Tax Rate — Environment Variable, BigDecimal in Service

**Decision**: Read `billing.tax-rate` from `application.yml` as a `BigDecimal` (e.g., `0.18` for 18%) via `@Value("${billing.tax-rate:0.00}")`. Default is `0.00` (no tax).

**Rationale**: Spec requires zero-config deployment with 0% default. `@Value` with default covers this without requiring `@ConfigurationProperties` class for a single value.

---

## Constitution Check (Pre-Design)

| Gate | Status | Notes |
|------|--------|-------|
| Spec-Driven | ✅ PASS | spec.md approved, all 5 US defined |
| HIPAA-First | ✅ PASS | Invoice amounts are financial PHI; audit log on every mutation |
| Test-First | ✅ PASS | tasks.md will enforce test tasks before implementation |
| Layered Architecture | ✅ PASS | Controller → Service → Repository maintained |
| RBAC | ✅ PASS | NURSE denied; DOCTOR scoped; ADMIN-only for cancel/write-off/report |
| Tech Stack | ✅ PASS | Spring Boot 3.2.x, Java 17, PostgreSQL 15, same Maven module |
| Complexity | ✅ PASS | No new services/frameworks; mirrors existing module patterns |

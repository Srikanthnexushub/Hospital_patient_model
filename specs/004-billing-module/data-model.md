# Data Model: 004-billing-module

**Branch**: `004-billing-module`
**Date**: 2026-02-20
**Flyway**: V12–V16 (V11 is the existing ceiling)

---

## Entity Overview

```
invoices (1) ──────────── (N) invoice_line_items
invoices (1) ──────────── (N) invoice_payments
invoices (1) ──────────── (N) invoice_audit_log
invoices (1) ──────────── (1) appointments     [FK, read-only — Module 3]
invoices (N) ──────────── (1) patients          [FK, read-only — Module 1]
invoice_id_sequences               [standalone — per-year counter]
```

---

## 1. `invoices` table (V12)

**Java entity**: `Invoice`
**Primary key**: `invoice_id` VARCHAR(16) — format `INV2026000001`

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| invoice_id | VARCHAR(16) | PK NOT NULL | INV + YYYY + 5-digit seq |
| appointment_id | VARCHAR(14) | UNIQUE NOT NULL FK→appointments | One invoice per appointment |
| patient_id | VARCHAR(12) | NOT NULL FK→patients | Denormalised for query performance |
| doctor_id | VARCHAR(12) | NOT NULL | Doctor at time of invoice |
| status | VARCHAR(20) | NOT NULL DEFAULT 'DRAFT' | See InvoiceStatus enum |
| total_amount | NUMERIC(12,2) | NOT NULL | Sum of all line item totals |
| discount_percent | NUMERIC(5,2) | NOT NULL DEFAULT 0.00 | 0.00–100.00 |
| discount_amount | NUMERIC(12,2) | NOT NULL DEFAULT 0.00 | total_amount × discount_percent/100 |
| tax_rate | NUMERIC(5,2) | NOT NULL DEFAULT 0.00 | Configured per deployment |
| tax_amount | NUMERIC(12,2) | NOT NULL DEFAULT 0.00 | net_amount × tax_rate/100 |
| net_amount | NUMERIC(12,2) | NOT NULL | total_amount − discount_amount |
| amount_due | NUMERIC(12,2) | NOT NULL | net_amount + tax_amount (decreases with payments) |
| amount_paid | NUMERIC(12,2) | NOT NULL DEFAULT 0.00 | Running total of payments received |
| notes | TEXT | nullable | Optional free-text notes |
| cancel_reason | TEXT | nullable | Required when status=CANCELLED or WRITTEN_OFF |
| version | INTEGER | NOT NULL DEFAULT 0 | Optimistic locking (@Version) |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | Immutable |
| created_by | VARCHAR(100) | NOT NULL | Staff username |
| updated_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | Updated on each mutation |
| updated_by | VARCHAR(100) | NOT NULL | Staff username |

**Constraints**:
```sql
CONSTRAINT chk_discount_percent CHECK (discount_percent >= 0 AND discount_percent <= 100)
CONSTRAINT chk_amount_paid_positive CHECK (amount_paid >= 0)
CONSTRAINT chk_total_amount_positive CHECK (total_amount >= 0)
```

**InvoiceStatus enum**:
```
DRAFT → ISSUED → PARTIALLY_PAID → PAID
              ↘ CANCELLED (from DRAFT, ISSUED)
              ↘ WRITTEN_OFF (from ISSUED, PARTIALLY_PAID)
```

---

## 2. `invoice_line_items` table (V13)

**Java entity**: `InvoiceLineItem`
**Primary key**: `id` BIGSERIAL (auto-generated)

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| id | BIGINT | PK GENERATED ALWAYS AS IDENTITY | |
| invoice_id | VARCHAR(16) | NOT NULL FK→invoices | |
| service_code | VARCHAR(20) | nullable | e.g. CONS001, LAB002 |
| description | TEXT | NOT NULL | e.g. "General Consultation" |
| quantity | INTEGER | NOT NULL CHECK > 0 | |
| unit_price | NUMERIC(10,2) | NOT NULL CHECK > 0 | |
| line_total | NUMERIC(12,2) | NOT NULL | quantity × unit_price (computed at insert) |

**Constraints**:
```sql
CONSTRAINT chk_quantity_positive CHECK (quantity > 0)
CONSTRAINT chk_unit_price_positive CHECK (unit_price > 0)
```

**Note**: Line items are **immutable** — inserted once when invoice is created, never updated.

---

## 3. `invoice_payments` table (V14)

**Java entity**: `InvoicePayment`
**Primary key**: `id` BIGSERIAL (auto-generated)

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| id | BIGINT | PK GENERATED ALWAYS AS IDENTITY | |
| invoice_id | VARCHAR(16) | NOT NULL FK→invoices | |
| amount | NUMERIC(12,2) | NOT NULL CHECK > 0 | |
| payment_method | VARCHAR(20) | NOT NULL | CASH, CARD, INSURANCE, BANK_TRANSFER, CHEQUE |
| reference_number | VARCHAR(100) | nullable | Card auth code, cheque number, etc. |
| notes | TEXT | nullable | |
| paid_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | |
| recorded_by | VARCHAR(100) | NOT NULL | Staff username who recorded it |

**Constraints**:
```sql
CONSTRAINT chk_payment_amount_positive CHECK (amount > 0)
```

**Note**: Payments are **immutable** (append-only). No update or delete.

**PaymentMethod enum**: `CASH`, `CARD`, `INSURANCE`, `BANK_TRANSFER`, `CHEQUE`

---

## 4. `invoice_audit_log` table (V15)

**Java entity**: `InvoiceAuditLog`
**Primary key**: `id` BIGSERIAL (auto-generated)

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| id | BIGINT | PK GENERATED ALWAYS AS IDENTITY | |
| invoice_id | VARCHAR(16) | NOT NULL | No FK — audit survives independently |
| action | VARCHAR(30) | NOT NULL | CREATE, ISSUE, PAYMENT, CANCEL, WRITE_OFF |
| from_status | VARCHAR(20) | nullable | null on CREATE |
| to_status | VARCHAR(20) | NOT NULL | |
| performed_by | VARCHAR(100) | NOT NULL | |
| performed_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | |
| details | TEXT | nullable | Extra context (reason, amount, method) |

**Note**: No `@Version` field — append-only, never updated.

---

## 5. `invoice_id_sequences` table (V12)

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| year | INTEGER | PK NOT NULL | Calendar year |
| last_sequence | INTEGER | NOT NULL DEFAULT 0 | Incremented under PESSIMISTIC_WRITE lock |

---

## Java Entity Definitions

### `Invoice.java`
```java
@Entity @Table(name = "invoices")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Invoice {
    @Id @Column(name = "invoice_id", length = 16) private String invoiceId;
    @Column(name = "appointment_id", length = 14, nullable = false, unique = true) private String appointmentId;
    @Column(name = "patient_id", length = 12, nullable = false) private String patientId;
    @Column(name = "doctor_id", length = 12, nullable = false) private String doctorId;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false) @Builder.Default
    private InvoiceStatus status = InvoiceStatus.DRAFT;
    @Column(name = "total_amount", precision = 12, scale = 2, nullable = false) private BigDecimal totalAmount;
    @Column(name = "discount_percent", precision = 5, scale = 2, nullable = false) @Builder.Default private BigDecimal discountPercent = BigDecimal.ZERO;
    @Column(name = "discount_amount", precision = 12, scale = 2, nullable = false) @Builder.Default private BigDecimal discountAmount = BigDecimal.ZERO;
    @Column(name = "tax_rate", precision = 5, scale = 2, nullable = false) private BigDecimal taxRate;
    @Column(name = "tax_amount", precision = 12, scale = 2, nullable = false) @Builder.Default private BigDecimal taxAmount = BigDecimal.ZERO;
    @Column(name = "net_amount", precision = 12, scale = 2, nullable = false) private BigDecimal netAmount;
    @Column(name = "amount_due", precision = 12, scale = 2, nullable = false) private BigDecimal amountDue;
    @Column(name = "amount_paid", precision = 12, scale = 2, nullable = false) @Builder.Default private BigDecimal amountPaid = BigDecimal.ZERO;
    @Column(name = "notes", columnDefinition = "TEXT") private String notes;
    @Column(name = "cancel_reason", columnDefinition = "TEXT") private String cancelReason;
    @Version @Column(name = "version", nullable = false) @Builder.Default private Integer version = 0;
    @Column(name = "created_at", nullable = false, updatable = false) private OffsetDateTime createdAt;
    @Column(name = "created_by", length = 100, nullable = false, updatable = false) private String createdBy;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;
    @Column(name = "updated_by", length = 100, nullable = false) private String updatedBy;
}
```

### `InvoiceLineItem.java`
```java
@Entity @Table(name = "invoice_line_items")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InvoiceLineItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id") private Long id;
    @Column(name = "invoice_id", length = 16, nullable = false) private String invoiceId;
    @Column(name = "service_code", length = 20) private String serviceCode;
    @Column(name = "description", columnDefinition = "TEXT", nullable = false) private String description;
    @Column(name = "quantity", nullable = false) private Integer quantity;
    @Column(name = "unit_price", precision = 10, scale = 2, nullable = false) private BigDecimal unitPrice;
    @Column(name = "line_total", precision = 12, scale = 2, nullable = false) private BigDecimal lineTotal;
}
```

### `InvoicePayment.java`
```java
@Entity @Table(name = "invoice_payments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InvoicePayment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id") private Long id;
    @Column(name = "invoice_id", length = 16, nullable = false) private String invoiceId;
    @Column(name = "amount", precision = 12, scale = 2, nullable = false) private BigDecimal amount;
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 20, nullable = false) private PaymentMethod paymentMethod;
    @Column(name = "reference_number", length = 100) private String referenceNumber;
    @Column(name = "notes", columnDefinition = "TEXT") private String notes;
    @Column(name = "paid_at", nullable = false) private OffsetDateTime paidAt;
    @Column(name = "recorded_by", length = 100, nullable = false) private String recordedBy;
}
```

### `InvoiceAuditLog.java`
```java
@Entity @Table(name = "invoice_audit_log")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InvoiceAuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id") private Long id;
    @Column(name = "invoice_id", length = 16, nullable = false) private String invoiceId;
    @Column(name = "action", length = 30, nullable = false) private String action;
    @Column(name = "from_status", length = 20) private String fromStatus;
    @Column(name = "to_status", length = 20, nullable = false) private String toStatus;
    @Column(name = "performed_by", length = 100, nullable = false) private String performedBy;
    @Column(name = "performed_at", nullable = false) private OffsetDateTime performedAt;
    @Column(name = "details", columnDefinition = "TEXT") private String details;
}
```

---

## Status Transition Machine

```
         ┌─────────────────────────────────────────────────────┐
         │                                                     │
  CREATE │                                            ADMIN only│
         ▼                                                     │
      DRAFT ──ISSUE──► ISSUED ──PAYMENT──► PARTIALLY_PAID     │
         │                │                      │             │
    CANCEL│           CANCEL│            WRITE_OFF│  CANCEL    │
         ▼                ▼                      ▼             │
    CANCELLED         CANCELLED           WRITTEN_OFF          │
                          │                                    │
                    PAYMENT (full)──────────────────────► PAID ┘
                                                (terminal — no transitions)
```

| Action | From Status(es) | To Status | Roles |
|--------|-----------------|-----------|-------|
| ISSUE | DRAFT | ISSUED | RECEPTIONIST, ADMIN |
| PAYMENT (partial) | ISSUED, PARTIALLY_PAID | PARTIALLY_PAID | RECEPTIONIST, ADMIN |
| PAYMENT (full) | ISSUED, PARTIALLY_PAID | PAID | RECEPTIONIST, ADMIN |
| CANCEL | DRAFT, ISSUED | CANCELLED | ADMIN |
| WRITE_OFF | ISSUED, PARTIALLY_PAID | WRITTEN_OFF | ADMIN |

**Terminal states**: PAID, CANCELLED, WRITTEN_OFF — no further transitions allowed.

---

## Indexes (V16)

```sql
-- Invoice lookups
CREATE INDEX idx_invoices_patient_id     ON invoices(patient_id);
CREATE INDEX idx_invoices_appointment_id ON invoices(appointment_id);
CREATE INDEX idx_invoices_status         ON invoices(status);
CREATE INDEX idx_invoices_created_at     ON invoices(created_at DESC);
CREATE INDEX idx_invoices_doctor_id      ON invoices(doctor_id);

-- Line items
CREATE INDEX idx_invoice_line_items_invoice_id ON invoice_line_items(invoice_id);

-- Payments
CREATE INDEX idx_invoice_payments_invoice_id   ON invoice_payments(invoice_id);

-- Audit
CREATE INDEX idx_invoice_audit_invoice_id      ON invoice_audit_log(invoice_id);
CREATE INDEX idx_invoice_audit_performed_at    ON invoice_audit_log(performed_at DESC);
```

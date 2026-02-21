# Quickstart: 004-billing-module

**Branch**: `004-billing-module`
**Date**: 2026-02-20
**Prerequisites**: Modules 1 (Patient), 2 (Auth), 3 (Appointment Scheduling) complete and running

---

## Integration Test Scenarios

These scenarios map directly to the acceptance criteria in `spec.md`. Each scenario is self-contained and uses the seed data defined below.

---

### Seed Data

All integration tests share this base setup (created once in `@BeforeEach` / `@Sql`):

```sql
-- Seed patient (Module 1 — do not modify)
INSERT INTO patients (patient_id, first_name, last_name, ...)
VALUES ('P2025001', 'John', 'Doe', ...);

-- Seed staff (Module 2 — do not modify)
-- receptionist1 / RECEPTIONIST, drsmith / DOCTOR, admin1 / ADMIN, nurse1 / NURSE
-- (already seeded by V5 migration)

-- Seed appointment (Module 3 — do not modify)
INSERT INTO appointments (appointment_id, patient_id, doctor_id, appointment_date, status)
VALUES ('APT2025000001', 'P2025001', 'drsmith', '2025-06-15', 'COMPLETED');

-- Seed second appointment for duplicate-invoice test
INSERT INTO appointments (appointment_id, patient_id, doctor_id, appointment_date, status)
VALUES ('APT2025000002', 'P2025001', 'drsmith', '2025-06-20', 'COMPLETED');
```

---

### Scenario 1 — US1: Generate Invoice (Happy Path)

**Goal**: Verify invoice creation with correct monetary calculations.

```
POST /api/v1/invoices
Authorization: Bearer <RECEPTIONIST token>

{
  "appointmentId": "APT2025000001",
  "lineItems": [
    { "description": "General Consultation", "quantity": 1, "unitPrice": 200.00, "serviceCode": "CONS001" },
    { "description": "Blood Panel", "quantity": 2, "unitPrice": 150.00, "serviceCode": "LAB002" }
  ],
  "discountPercent": 10.00,
  "notes": "Test invoice"
}
```

**Expected**:
- HTTP 201
- `invoiceId` matches pattern `INV\d{9}` (e.g. `INV2026000001`)
- `status = "DRAFT"`
- `totalAmount = 500.00` (200 + 300)
- `discountAmount = 50.00` (500 × 10%)
- `netAmount = 450.00`
- `taxAmount = 0.00` (default 0% tax)
- `amountDue = 450.00`
- `lineItems` has 2 entries

---

### Scenario 2 — US1: Reject Duplicate Invoice

**Goal**: Second invoice for the same appointment returns 409.

**Pre-condition**: Invoice already exists for `APT2025000001` (from Scenario 1 or seeded directly).

```
POST /api/v1/invoices
Authorization: Bearer <RECEPTIONIST token>

{ "appointmentId": "APT2025000001", "lineItems": [{ "description": "Test", "quantity": 1, "unitPrice": 100.00 }] }
```

**Expected**: HTTP 409, message contains "already exists"

---

### Scenario 3 — US1: NURSE Denied

```
POST /api/v1/invoices
Authorization: Bearer <NURSE token>

{ "appointmentId": "APT2025000002", "lineItems": [{ "description": "Test", "quantity": 1, "unitPrice": 50.00 }] }
```

**Expected**: HTTP 403

---

### Scenario 4 — US2: List Invoices (RECEPTIONIST sees all)

**Pre-condition**: At least 2 invoices exist across different patients/doctors.

```
GET /api/v1/invoices?page=0&size=20
Authorization: Bearer <RECEPTIONIST token>
```

**Expected**:
- HTTP 200
- `content` array, `totalElements ≥ 1`
- Results ordered by `createdAt DESC`

---

### Scenario 5 — US2: Filter by Status

```
GET /api/v1/invoices?status=DRAFT
Authorization: Bearer <ADMIN token>
```

**Expected**: All returned invoices have `status = "DRAFT"`

---

### Scenario 6 — US2: DOCTOR Scoped View

**Pre-condition**: Invoice `INV-A` is for `drsmith`'s appointment; `INV-B` is for a different doctor.

```
GET /api/v1/invoices
Authorization: Bearer <DOCTOR/drsmith token>
```

**Expected**: Response does NOT contain `INV-B`; only `INV-A` (drsmith's patient) is returned.

---

### Scenario 7 — US2: Get Invoice Detail

**Pre-condition**: Invoice `INV2026000001` exists with 2 line items.

```
GET /api/v1/invoices/INV2026000001
Authorization: Bearer <RECEPTIONIST token>
```

**Expected**:
- HTTP 200
- `lineItems` array has 2 entries with correct values
- `payments` array is empty (no payments yet)
- `patientName` and `doctorName` are populated (joined from patient/staff tables)

---

### Scenario 8 — US3: Partial Payment

**Pre-condition**: Invoice for 450.00 in ISSUED status.

**Step 1** — Issue the invoice (setup):
```
PATCH /api/v1/invoices/{invoiceId}/status   ← N/A — use a different mechanism or seed ISSUED directly
```
> Note: Invoicing goes DRAFT → ISSUED via the `/status` endpoint with `action=ISSUE` OR seed data can set status='ISSUED' directly.

**Step 2** — Record partial payment:
```
POST /api/v1/invoices/{invoiceId}/payments
Authorization: Bearer <RECEPTIONIST token>

{ "amount": 100.00, "paymentMethod": "CASH" }
```

**Expected**:
- HTTP 200
- `amountPaid = 100.00`
- `amountDue = 350.00`
- `status = "PARTIALLY_PAID"`
- `payments` array has 1 entry

---

### Scenario 9 — US3: Full Payment (PARTIALLY_PAID → PAID)

**Pre-condition**: Invoice with `amountDue = 350.00` and status `PARTIALLY_PAID`.

```
POST /api/v1/invoices/{invoiceId}/payments
Authorization: Bearer <RECEPTIONIST token>

{ "amount": 350.00, "paymentMethod": "CARD", "referenceNumber": "AUTH-XYZ123" }
```

**Expected**:
- HTTP 200
- `amountPaid = 450.00`
- `amountDue = 0.00`
- `status = "PAID"`

---

### Scenario 10 — US3: Overpayment

**Pre-condition**: Invoice with `amountDue = 50.00` and status `PARTIALLY_PAID`.

```
POST /api/v1/invoices/{invoiceId}/payments
Authorization: Bearer <RECEPTIONIST token>

{ "amount": 100.00, "paymentMethod": "CASH" }
```

**Expected**:
- HTTP 200
- `amountDue = -50.00` (credit)
- `status = "PAID"`

---

### Scenario 11 — US3: Payment Rejected on CANCELLED Invoice

```
POST /api/v1/invoices/{cancelledInvoiceId}/payments
Authorization: Bearer <RECEPTIONIST token>

{ "amount": 50.00, "paymentMethod": "CASH" }
```

**Expected**: HTTP 409, message contains "CANCELLED"

---

### Scenario 12 — US4: Cancel DRAFT Invoice (ADMIN)

**Pre-condition**: Invoice in DRAFT status.

```
PATCH /api/v1/invoices/{invoiceId}/status
Authorization: Bearer <ADMIN token>

{ "action": "CANCEL", "reason": "Created in error" }
```

**Expected**:
- HTTP 200
- `status = "CANCELLED"`
- `cancelReason = "Created in error"`

---

### Scenario 13 — US4: Cancel PAID Invoice Rejected

```
PATCH /api/v1/invoices/{paidInvoiceId}/status
Authorization: Bearer <ADMIN token>

{ "action": "CANCEL", "reason": "Trying to cancel paid invoice" }
```

**Expected**: HTTP 409, message contains invalid transition

---

### Scenario 14 — US4: Write-Off Rejected for RECEPTIONIST

```
PATCH /api/v1/invoices/{invoiceId}/status
Authorization: Bearer <RECEPTIONIST token>

{ "action": "WRITE_OFF", "reason": "Bad debt" }
```

**Expected**: HTTP 403

---

### Scenario 15 — US5: Financial Report (ADMIN)

**Pre-condition**: Seed invoices for January 2026:
- 3 × PAID invoices totalling 900.00
- 1 × PARTIALLY_PAID invoice (amountDue = 200.00)
- 1 × CANCELLED invoice (totalAmount = 100.00)
- Payments: 700.00 CASH, 200.00 CARD

```
GET /api/v1/reports/financial?dateFrom=2026-01-01&dateTo=2026-01-31
Authorization: Bearer <ADMIN token>
```

**Expected**:
- HTTP 200
- `invoiceCount ≥ 5`
- `paidCount = 3`
- `totalCollected = 900.00` (sum of all payments)
- `byPaymentMethod.CASH = 700.00`
- `byPaymentMethod.CARD = 200.00`
- `totalCancelled = 100.00`

---

### Scenario 16 — US5: Empty Date Range Returns Zeros

```
GET /api/v1/reports/financial?dateFrom=2020-01-01&dateTo=2020-01-31
Authorization: Bearer <ADMIN token>
```

**Expected**:
- HTTP 200
- All numeric fields = 0.00 or 0
- No error

---

### Scenario 17 — US5: RECEPTIONIST Denied Financial Report

```
GET /api/v1/reports/financial?dateFrom=2026-01-01&dateTo=2026-01-31
Authorization: Bearer <RECEPTIONIST token>
```

**Expected**: HTTP 403

---

## RBAC Matrix

| Endpoint | RECEPTIONIST | DOCTOR | NURSE | ADMIN |
|----------|:---:|:---:|:---:|:---:|
| POST /invoices | ✅ | ❌ 403 | ❌ 403 | ✅ |
| GET /invoices | ✅ all | ✅ own | ❌ 403 | ✅ all |
| GET /invoices/{id} | ✅ | ✅ own | ❌ 403 | ✅ |
| POST /invoices/{id}/payments | ✅ | ❌ 403 | ❌ 403 | ✅ |
| PATCH /invoices/{id}/status | ❌ 403 | ❌ 403 | ❌ 403 | ✅ |
| GET /patients/{id}/invoices | ✅ | ✅ own | ❌ 403 | ✅ |
| GET /reports/financial | ❌ 403 | ❌ 403 | ❌ 403 | ✅ |

---

## Monetary Calculation Reference

Given: 2 line items (qty=1, price=200.00) and (qty=2, price=150.00), discountPercent=10, taxRate=0:

```
totalAmount    = (1 × 200.00) + (2 × 150.00) = 500.00
discountAmount = 500.00 × 10.00 / 100        =  50.00
netAmount      = 500.00 - 50.00              = 450.00
taxAmount      = 450.00 × 0.00 / 100         =   0.00
amountDue      = 450.00 + 0.00               = 450.00
```

All arithmetic uses `BigDecimal.setScale(2, RoundingMode.HALF_UP)`.
